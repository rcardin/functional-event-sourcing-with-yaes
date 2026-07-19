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
    w.callCount("dispatch Role.FIX") shouldBe 0               // no repair
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

