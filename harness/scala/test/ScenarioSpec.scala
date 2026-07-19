package harness

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Script.*

/** The scenario matrix of harness/test/statemachine-test.sh (scenarios DRY, A-T) plus the
  * semantics bullets of the design doc, ported to in-memory scripted handlers.
  */
class ScenarioSpec extends AnyFlatSpec with Matchers:

  def runLoop(w: TestWorld, cfg: Config = Config()): LoopExit =
    Machine.runOnce(1)(using cfg, w.github, w.git, w.agents, w.gates, w.status, w.notifier, w.fs, w.clock)

  // ---- STOP.md ----------------------------------------------------------------------------

  "The machine" should "exit ManualStop (rc 10) when STOP.md is present, touching nothing" in {
    val w = TestWorld()
    w.stopFile = true

    val exit = runLoop(w)

    exit shouldBe LoopExit.ManualStop
    exit.rc shouldBe 10
    w.called("gh issue list") shouldBe false
    w.called("gh issue edit") shouldBe false
    w.calls shouldBe empty
  }

  // ---- Scenario F: idle must not latch ---------------------------------------------------

  it should "exit Idle (rc 11) with NO sentinel written when no in-progress or ready issue exists" in {
    val w = TestWorld()
    w.inProgress = None
    w.ready = None

    val exit = runLoop(w)

    exit shouldBe LoopExit.Idle
    exit.rc shouldBe 11
    w.files shouldBe empty                      // idle writes nothing, ever (PR #17 latch bug)
    w.called("gh issue edit") shouldBe false    // nothing started
    w.called("gh issue list --label in-progress") shouldBe true
    w.called("gh issue list --label ready") shouldBe true
  }

  it should "resume an in-progress issue before considering ready ones" in {
    val w = TestWorld()
    w.inProgress = Some(777)
    w.ready = Some(999)

    runLoop(w, Config(dryRun = true))

    w.called("gh issue view 777 --json title,body") shouldBe true
    w.called("gh issue view 999") shouldBe false
  }

  // ---- Scenario DRY: DRY_RUN renders the worker prompt, no mutation ------------------------

  it should "stop at DryRun (rc 20) with the worker prompt rendered and zero mutations" in {
    val w = TestWorld()

    val exit = runLoop(w, Config(dryRun = true))

    exit shouldBe LoopExit.DryRun
    exit.rc shouldBe 20
    // the worker prompt was rendered with the issue body spliced in
    w.files("harness/logs/issue-999.prompt.txt") should include("AC1: implement the slice")
    // truly read-only: no label mutation, no branch, no fetch, no PR
    w.called("gh issue edit") shouldBe false
    w.called("gh pr create") shouldBe false
    w.called("git checkout") shouldBe false
    w.called("git fetch") shouldBe false
    w.phaseSeq shouldBe List("PICK", "DONE")
  }

  // ---- Scenario A: APPROVE happy path -> needs-review, exit 0 ------------------------------

  it should "reach a PR and needs-review on APPROVE (Scenario A)" in {
    val w = TestWorld()

    val exit = runLoop(w)

    exit shouldBe LoopExit.Success
    exit.rc shouldBe 0
    w.called("gh issue edit 999 --add-label in-progress --remove-label ready") shouldBe true
    w.callCount("gate FAST") shouldBe 1                       // one fast-gate pass only
    w.callCount("dispatch FIX") shouldBe 0               // no repair
    w.callCount("dispatch REVIEW") shouldBe 1
    // the review prompt got conventions, tamper report and the diff spliced in
    val reviewPrompt = w.files("harness/logs/issue-999-pass1.review.prompt.txt")
    reviewPrompt should include("Conventions: onion layout")
    reviewPrompt should include("Test-tamper report")
    reviewPrompt should include("src/main/scala/Slice.scala")
    w.commitMessages should have size 1
    w.commitMessages.head should include("feat(US-999): autonomous iteration — reviewer APPROVE, gate GREEN")
    w.pushedBranches shouldBe List("us-999")
    w.called("gh pr create --head us-999") shouldBe true
    w.prBodies.head should include("Closes #999")
    w.prBodies.head should include("Not auto-merged")
    w.called("gh issue edit 999 --add-label needs-review --remove-label in-progress") shouldBe true
    // no auto-merge machinery on the non-class-1 path
    w.called("gate CI-WAIT") shouldBe false
    w.called("gh pr merge") shouldBe false
    w.notifications shouldBe empty
    // logfile fields are repo-relative, never absolute
    w.events.foreach(e => e.logfile should not startWith "/")
    w.phaseSeq shouldBe List("PICK", "IMPL", "FAST_GATE", "REVIEW", "PR", "DONE")
  }

  // ---- Scenario M: class-2 SUCCESS -> stop-at-PR, no CI wait, no merge ---------------------

  it should "stop at the PR for a class-2 SUCCESS: needs-review, no CI wait, no merge (Scenario M)" in {
    val w = TestWorld()
    w.labels = List("ready", "class-2")

    val exit = runLoop(w)

    exit shouldBe LoopExit.Success
    w.called("gh issue edit 999 --add-label needs-review --remove-label in-progress") shouldBe true
    w.called("gate CI-WAIT") shouldBe false
    w.called("gh pr view 123 --json statusCheckRollup") shouldBe false
    w.called("gh pr merge") shouldBe false
  }

  // ---- Scenario J: class-1 SUCCESS + CI green -> auto-merge, flip blocked, notify ----------

  it should "auto-merge a class-1 SUCCESS after CI green, flip unblocked dependents and notify (Scenario J)" in {
    val w = TestWorld()
    w.labels = List("ready", "class-1")
    w.blockedIssues = List(555, 666)
    w.issueBodies = Map(555 -> "Blocked-by: #999\n", 666 -> "Blocked-by: #999\nBlocked-by: #777\n")
    w.issueStates = Map(777 -> "OPEN")

    val exit = runLoop(w)

    exit shouldBe LoopExit.Success
    w.called("gate CI-WAIT") shouldBe true                                  // CI wait ran
    w.called("gh pr merge 123 --squash --delete-branch") shouldBe true
    w.called("gh pr view 123 --json state") shouldBe true                   // merge verified
    w.called("--add-label needs-review") shouldBe false                     // auto-merge owns the fate
    w.called("gh issue edit 999 --remove-label in-progress") shouldBe true
    w.notifications shouldBe List("harness: #999 auto-merged (PR #123, CI green, reviewer APPROVE)")
    // blocked -> ready flip: 555's only dep is the just-merged issue; 666 still waits on #777
    w.called("gh issue edit 555 --add-label ready --remove-label blocked") shouldBe true
    w.called("gh issue edit 666 --add-label ready") shouldBe false
    // post-merge fetch so the next tick starts from the new main
    w.callCount("git fetch origin main") shouldBe 2
    w.phaseSeq shouldBe List("PICK", "IMPL", "FAST_GATE", "REVIEW", "PR", "CI_WAIT", "MERGE", "DONE")
  }

  // ---- Scenario B: REQUEST_CHANGES -> exactly one fix, re-gate, re-review APPROVE ----------

  it should "dispatch exactly one FIX on REQUEST_CHANGES, re-gate, and approve (Scenario B)" in {
    val w = TestWorld()
    w.reviewScripts = List(
      ReviewScript.Says("VERDICT: REQUEST_CHANGES"),
      ReviewScript.Says("VERDICT: APPROVE")
    )
    w.fixScripts = List(WorkerScript.Produces("1\t0\tsrc/main/scala/SliceFixed.scala"))

    val exit = runLoop(w)

    exit shouldBe LoopExit.Success
    w.callCount("dispatch FIX") shouldBe 1                 // exactly one fix
    w.callCount("gate FAST") shouldBe 2                         // re-gate, no third pass
    w.callCount("dispatch REVIEW") shouldBe 2
    w.called("gh issue edit 999 --add-label needs-review --remove-label in-progress") shouldBe true
    // the fix prompt carried the reviewer's complaint and was rendered per pass
    w.files("harness/logs/issue-999-pass1.fix.prompt.txt") should include("The independent reviewer requested changes")
    // one budget unit spent: the FIX phase event carries budget 1 (of 2)
    w.events.filter(_.phase == "FIX").map(_.budget).distinct shouldBe List(1)
    // the FIX dispatch was seeded with the prior cumulative patch
    w.calls.find(_.startsWith("dispatch FIX")).get should include(
      "currentPatch=harness/logs/issue-999-iter1.impl.patch"
    )
  }

  // ---- Scenario C: gate-RED exhausts the shared budget -> needs-human + audit PR -----------

  it should "exhaust the shared budget on repeated gate-RED and route to needs-human with an audit PR (Scenario C)" in {
    val w = TestWorld()
    w.gateResults = List(GateResult.Red, GateResult.Red, GateResult.Red)
    w.fixScripts = List(
      WorkerScript.Produces("1\t0\tsrc/main/scala/Fix1.scala"),
      WorkerScript.Produces("1\t0\tsrc/main/scala/Fix2.scala")
    )

    val exit = runLoop(w)

    exit shouldBe LoopExit.NeedsHuman
    exit.rc shouldBe 40
    w.callCount("dispatch FIX") shouldBe 2       // exactly two fixes (budget 2)
    w.callCount("gate FAST") shouldBe 3          // 2 fixes + final RED, no fourth pass
    w.callCount("dispatch REVIEW") shouldBe 0    // RED never renders a review prompt
    w.called("gh issue edit 999 --add-label needs-human --remove-label in-progress") shouldBe true
    w.called("gh pr create") shouldBe true       // PR still opened (audit trail)
    w.notifications shouldBe List("harness: #999 needs-human (gate-RED, gate RED)")
    w.commitMessages.head should include("self-repair budget exhausted (gate-RED), gate RED")
    w.prBodies.head should include("**Needs human** — self-repair budget of 2 exhausted on gate-RED (last gate RED)")
  }

  it should "exhaust the shared budget on repeated REQUEST_CHANGES via the same pool" in {
    val w = TestWorld()
    w.reviewScripts = List(
      ReviewScript.Says("VERDICT: REQUEST_CHANGES"),
      ReviewScript.Says("VERDICT: REQUEST_CHANGES"),
      ReviewScript.Says("VERDICT: REQUEST_CHANGES")
    )
    w.fixScripts = List(
      WorkerScript.Produces("1\t0\tsrc/main/scala/Fix1.scala"),
      WorkerScript.Produces("1\t0\tsrc/main/scala/Fix2.scala")
    )

    val exit = runLoop(w)

    exit shouldBe LoopExit.NeedsHuman
    w.callCount("dispatch FIX") shouldBe 2
    w.callCount("dispatch REVIEW") shouldBe 3
    w.called("gh issue edit 999 --add-label needs-human --remove-label in-progress") shouldBe true
    w.notifications shouldBe List("harness: #999 needs-human (REQUEST_CHANGES, gate GREEN)")
    w.commitMessages.head should include("self-repair budget exhausted (REQUEST_CHANGES), gate GREEN")
  }

  // ---- Scenario D: IMPL dispatch timeout -> rc 50, budget untouched, nothing dispatched ----

  it should "exit InfraFault (rc 50) on an IMPL dispatch timeout: no budget spent, no gates, no PR, resumable (Scenario D)" in {
    val w = TestWorld()
    w.implScript = WorkerScript.TimedOut
    w.fixScripts = List(WorkerScript.Produces(newFilePatch)) // must never be consumed

    val exit = runLoop(w)

    exit shouldBe LoopExit.InfraFault
    exit.rc shouldBe 50
    w.callCount("dispatch FIX") shouldBe 0                    // zero FIX (no budget spent)
    w.callCount("gate FAST") shouldBe 0                       // a timed-out worker never reaches the gates
    w.called("gh pr create") shouldBe false
    w.called("needs-human") shouldBe false
    w.called("gh issue edit 999 --add-label in-progress --remove-label ready") shouldBe true
    w.callCount("--remove-label in-progress") shouldBe 0      // resumable next tick
    w.phaseSeq shouldBe List("PICK", "IMPL", "DONE")          // stops at the timed-out IMPL
    w.events.find(e => e.phase == "IMPL" && e.state == "red").get.detail shouldBe "timeout"
    w.notifications shouldBe List("harness: infra fault — loop exited rc=50 for inspection (issue stays in-progress)")
  }

