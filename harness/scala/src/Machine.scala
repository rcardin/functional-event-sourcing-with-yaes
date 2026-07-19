package harness

import in.rcard.yaes.Raise

/** The loop state machine for one US, ported from `harness/loop.sh` iterate(). */
object Machine:

  val LogDir = "harness/logs"

  /** The four CUR_* globals of loop.sh: the status-event context. iterate() keeps them
    * current; emit() only reads them, so a terminal DONE from the driver still carries the
    * right issue.
    */
  final class Cursor:
    var iter: Int     = 0
    var issue: String = ""
    var pass: Int     = 0
    var budget: Int   = 0

  /** Detail sanitization: never model-controlled, but strip anything that could break out of
    * the JSON string anyway (backslash, double quote, newlines).
    */
  private[harness] def sanitizeDetail(detail: String): String =
    detail.replace("\\", "").replace("\"", "").replace("\n", " ")

  private def emit(cur: Cursor, phase: String, state: String, logfile: String = "", detail: String = "")(using
      log: StatusLog
  ): Unit =
    log.append(
      StatusEvent(cur.iter, cur.issue, phase, state, cur.pass, cur.budget, logfile, sanitizeDetail(detail))
    )

  /** render_template: each line containing the literal `{{KEY}}` is replaced by the spliced
    * content (whole-line replacement, embedded newlines preserved), one key per pass.
    */
  private[harness] def renderTemplate(template: String, splices: (String, String)*): String =
    splices.foldLeft(template) { case (acc, (key, content)) =>
      acc.linesIterator
        .flatMap { line =>
          if line.contains(s"{{$key}}") then content.linesIterator else Iterator(line)
        }
        .mkString("\n")
    }

  /** One driver tick: folds the infra-fault channel to LoopExit.InfraFault (rc 50) and emits
    * the terminal DONE status event, exactly like the bash driver.
    */
  def runOnce(n: Int)(using
      Config,
      GitHub,
      Git,
      AgentDispatch,
      GateRunner,
      StatusLog,
      Notify,
      HarnessFs,
      Clock
  ): LoopExit =
    val cur = Cursor()
    val exit = Raise.fold(iterate(n, cur)) { (_: InfraFault) =>
      // rc-50 exits fire the notify seam: exit for inspection, issue stays in-progress.
      summon[Notify].notify("harness: infra fault — loop exited rc=50 for inspection (issue stays in-progress)")
      LoopExit.InfraFault
    }(identity)
    emit(cur, "DONE", "end", detail = s"rc=${exit.rc}")
    exit

  /** One US, start to terminal. Infra faults short-circuit via Raise[InfraFault]: no code
    * past a raise can spend repair budget or dispatch a FIX.
    */
  def iterate(n: Int, cur: Cursor)(using
      cfg: Config,
      gh: GitHub,
      git: Git,
      agents: AgentDispatch,
      gates: GateRunner,
      log: StatusLog,
      notify: Notify,
      fs: HarnessFs,
      clock: Clock
  )(using Raise[InfraFault]): LoopExit =
    // STOP.md is a MANUAL kill-switch only: the loop never writes it itself.
    if fs.stopRequested() then return LoopExit.ManualStop

    // Pick US (deterministic, no LLM): resume an in-progress one, else oldest ready.
    // No issue = transient idle — nothing is written, nothing is labelled, so the very next
    // tick resumes on its own when a US goes ready (the idle state must never latch).
    val issue = gh.inProgressIssue().orElse(gh.oldestReadyIssue()) match
      case None    => return LoopExit.Idle
      case Some(i) => i
    cur.iter = n; cur.issue = issue.toString; cur.pass = 0; cur.budget = cfg.repairBudget
    emit(cur, "PICK", "ok", detail = s"issue=$issue")

    // Render the worker prompt with the issue body injected (read-only).
    val bodyFile = s"$LogDir/issue-$issue.body.md"
    fs.write(bodyFile, gh.issueTitleAndBody(issue))
    val workerPromptFile = s"$LogDir/issue-$issue.prompt.txt"
    fs.write(workerPromptFile, renderTemplate(fs.readTemplate(Template.Iterate), "ISSUE" -> fs.read(bodyFile)))

    // Auto-merge is earned by class-1 only. Detect the class once, at pick time.
    val isClass1 = gh.issueLabels(issue).contains("class-1")

    // Dry run stops here — before ANY git/label mutation, so it is truly read-only.
    if cfg.dryRun then return LoopExit.DryRun

    // Require a clean tree on a fresh branch off main. Serial loop: one US at a time.
    // These are die() paths in bash (exit 1): fatal misconfiguration, not part of the
    // rc 0..50 state machine, so they surface as exceptions.
    if !git.statusClean() then throw IllegalStateException("working tree not clean — refusing to start")
    // Stale-base guard: everything downstream is measured against origin/main; no fallback.
    if !git.fetchOriginMain() then
      throw IllegalStateException("cannot fetch origin/main — refusing to run against a stale base")
    val branch = s"us-$issue"
    if !git.checkoutBranch(branch) then throw IllegalStateException("cannot branch off origin/main")

    // Mark in-progress so a crashed run resumes the same US next tick.
    gh.editLabels(issue, add = List("in-progress"), remove = List("ready"))

    // --- bounded self-repair state -------------------------------------------------------
    // Declared BEFORE the initial dispatch: a patch-guard rejection on the very first worker
    // patch sets outcome/failureKind and skips the loop straight to the terminal.
    var budget                           = cfg.repairBudget
    var pass                             = 0
    var outcome: Option[Outcome]         = None
    var gateStatus                       = ""
    var failureKind: Option[FailureKind] = None
    var currentPatch: Option[String]     = None
    val reviewFile                       = s"$LogDir/issue-$issue-review.md"
    fs.write(reviewFile, "") // empty until the first review
    var reviewed = false

    // Initial worker dispatch (fresh context), crossing the patch seam. The tree the worker
    // edited is never committed directly.
    val implLog   = s"$LogDir/issue-$issue-iter$n.claude.log"
    val implPatch = s"$LogDir/issue-$issue-iter$n.impl.patch"
    emit(cur, "IMPL", "start", implLog)
    stagePatch(Role.IMPL, workerPromptFile, implPatch, currentPatch) match
      case StageResult.Timeout =>
        emit(cur, "IMPL", "red", implLog, "timeout")
        Raise.raise(InfraFault("IMPL worker timed out — a half-finished worker must not reach the gates"))
      case StageResult.ApplyFail =>
        emit(cur, "IMPL", "red", implLog, "patch apply conflict")
        Raise.raise(InfraFault("IMPL patch did not apply — infra fault, no budget spent"))
      case StageResult.Empty =>
        emit(cur, "IMPL", "ok", implLog, "no diff")
        return LoopExit.NothingMade
      case StageResult.Protected =>
        emit(cur, "IMPL", "red", implLog, "protected-path")
        outcome = Some(Outcome.Fail); failureKind = Some(FailureKind.ProtectedPath); gateStatus = "SKIPPED"
      case StageResult.Oversize =>
        emit(cur, "IMPL", "red", implLog, "oversized patch")
        outcome = Some(Outcome.Fail); failureKind = Some(FailureKind.OversizedPatch); gateStatus = "SKIPPED"
      case StageResult.Ok(p) =>
        currentPatch = Some(p)
        emit(cur, "IMPL", "ok", implLog)

    // The fixer dispatch across the patch seam plus the mapping of its StageResult onto the
    // repair loop's control flow (bash dispatch_fix + handle_fix_result). Infra faults raise;
    // guard rejections and an empty fix become the terminal FAIL; Ok advances currentPatch.
    def fixRound(pass: Int, failFile: String): Unit =
      val fixPromptFile = s"$LogDir/issue-$issue-pass$pass.fix.prompt.txt"
      fs.write(
        fixPromptFile,
        renderTemplate(fs.readTemplate(Template.Fix), "ISSUE" -> fs.read(bodyFile), "FAILURE" -> fs.read(failFile))
      )
      val fixLog   = s"$LogDir/issue-$issue-pass$pass.fix.claude.log"
      val fixPatch = s"$LogDir/issue-$issue-pass$pass.fix.patch"
      emit(cur, "FIX", "start", fixLog)
      stagePatch(Role.FIX, fixPromptFile, fixPatch, currentPatch) match
        case StageResult.Timeout =>
          emit(cur, "FIX", "red", fixLog, "timeout")
          Raise.raise(InfraFault("FIX worker timed out (infra fault); exiting without spending further budget"))
        case StageResult.ApplyFail =>
          emit(cur, "FIX", "red", fixLog, "patch apply conflict")
          Raise.raise(InfraFault("FIX patch did not apply (infra fault, no budget spent)"))
        case StageResult.Protected =>
          emit(cur, "FIX", "red", fixLog, "protected-path")
          outcome = Some(Outcome.Fail); failureKind = Some(FailureKind.ProtectedPath)
        case StageResult.Oversize =>
          emit(cur, "FIX", "red", fixLog, "oversized patch")
          outcome = Some(Outcome.Fail); failureKind = Some(FailureKind.OversizedPatch)
        case StageResult.Empty =>
          // The fixer reverted all prior work — route to needs-human, never re-gate an empty tree.
          emit(cur, "FIX", "red", fixLog, "empty fix")
          outcome = Some(Outcome.Fail); failureKind = Some(FailureKind.EmptyFix)
        case StageResult.Ok(p) =>
          emit(cur, "FIX", "ok", fixLog)
          currentPatch = Some(p)

    // --- bounded self-repair loop --------------------------------------------------------
    // Skipped entirely if the initial patch was already rejected (outcome set above).
    while outcome.isEmpty do
      pass += 1
      git.addAll() // stage so new files show in diff/gate/tamper
      cur.pass = pass
      val gateLog = s"$LogDir/issue-$issue-pass$pass.gate.log"
      emit(cur, "FAST_GATE", "start", gateLog)
      gates.run("FAST", cfg.gateCmd, cfg.gateTimeout, gateLog) match
        case GateResult.Timeout =>
          Raise.raise(InfraFault(s"FAST gate hit the ${cfg.gateTimeout}s timeout — infra fault, not a code failure"))
        case GateResult.Red =>
          gateStatus = "RED"
          failureKind = Some(FailureKind.GateRed)
          emit(cur, "FAST_GATE", "red", gateLog)
          if budget == 0 then outcome = Some(Outcome.Fail)
          else
            budget -= 1; cur.budget = budget
            val failFile = s"$LogDir/issue-$issue-pass$pass.failure.md"
            fs.write(
              failFile,
              s"## Fast-gate failure — `${cfg.gateCmd}` (compile under -Werror, then in-memory tests)\n\n" +
                s"Tail of the fast-gate log:\n\n```\n${fs.read(gateLog)}\n```\n"
            )
            fixRound(pass, failFile)
        case GateResult.Green =>
          gateStatus = "GREEN"
          emit(cur, "FAST_GATE", "ok", gateLog)

          // Tamper check feeds the reviewer (the harness surfaces, does not block).
          val tamperFile = s"$LogDir/issue-$issue-tamper.md"
          fs.write(tamperFile, tamperReport(currentPatch.map(git.applyNumstat).getOrElse("")))
          val diffFile = s"$LogDir/issue-$issue-diff.patch"
          fs.write(diffFile, git.diffCachedOriginMain())
          val reviewPromptFile = s"$LogDir/issue-$issue-pass$pass.review.prompt.txt"
          fs.write(
            reviewPromptFile,
            renderTemplate(
              fs.readTemplate(Template.Review),
              "ISSUE"       -> fs.read(bodyFile),
              "CONVENTIONS" -> fs.conventions(),
              "TAMPER"      -> fs.read(tamperFile),
              "DIFF"        -> fs.read(diffFile)
            )
          )
          emit(cur, "REVIEW", "start", reviewFile)
          agents.review(fs.read(reviewPromptFile), reviewFile) match
            case DispatchOutcome.TimedOut =>
              emit(cur, "REVIEW", "red", reviewFile, "timeout")
              Raise.raise(InfraFault("REVIEWER timed out — exiting without spending budget"))
            case DispatchOutcome.Done => ()
          reviewed = true

          // An empty (or whitespace-only) review is a crashed reviewer, not a verdict.
          if fs.read(reviewFile).isBlank then
            emit(cur, "REVIEW", "red", reviewFile, "empty review")
            Raise.raise(InfraFault("reviewer produced no output — infra fault (crashed or timed-out reviewer)"))

          // Grep, not parse. Missing sentinel -> REQUEST_CHANGES (fail safe, never auto-approve).
          val verdict = parseVerdict(fs.read(reviewFile)).getOrElse(Verdict.RequestChanges)
          emit(cur, "REVIEW", "ok", reviewFile, s"verdict=${verdictText(verdict)}")
          verdict match
            case Verdict.Approve =>
              outcome = Some(Outcome.Success)
            case Verdict.RequestChanges =>
              // REQUEST_CHANGES — spend from the same shared budget as gate-RED.
              failureKind = Some(FailureKind.ReviewChanges)
              if budget == 0 then outcome = Some(Outcome.Fail)
              else
                budget -= 1; cur.budget = budget
                val failFile = s"$LogDir/issue-$issue-pass$pass.failure.md"
                fs.write(
                  failFile,
                  s"## The independent reviewer requested changes\n\n${fs.read(reviewFile)}\n\n${fs.read(tamperFile)}"
                )
                fixRound(pass, failFile)
    end while

    // --- terminal: commit, push, PR (SUCCESS -> needs-review, FAIL -> needs-human) --------
    if failureKind.contains(FailureKind.EmptyFix) then ???
    git.addAll()
    if !git.anythingStaged() then return LoopExit.NothingMade

    val outcomeText = if outcome.contains(Outcome.Success) then "SUCCESS" else "FAIL"
    val kindText    = failureKind.map(_.text).getOrElse("?")
    val (label, commitTag, prNote) =
      if outcome.contains(Outcome.Success) && isClass1 then
        // no flip: the auto-merge path owns the issue's fate
        (
          "",
          s"reviewer APPROVE, gate $gateStatus",
          s"**Reviewer: APPROVE** · gate $gateStatus · class-1 — v4 auto-merge candidate: the loop merges after the required CI check goes green."
        )
      else if outcome.contains(Outcome.Success) then
        (
          "needs-review",
          s"reviewer APPROVE, gate $gateStatus",
          s"**Reviewer: APPROVE** · gate $gateStatus (containerized in-memory FAST tier green; the real-PG IT tier is judged by CI on this PR). Not class-1, so not auto-merged: a human reviews and merges."
        )
      else if failureKind.contains(FailureKind.ProtectedPath) || failureKind.contains(FailureKind.OversizedPatch)
      then ???
      else if failureKind.contains(FailureKind.EmptyFix) then ???
      else
        (
          "needs-human",
          s"self-repair budget exhausted ($kindText), gate $gateStatus",
          s"**Needs human** — self-repair budget of ${cfg.repairBudget} exhausted on $kindText (last gate $gateStatus). Opened for the audit trail; do NOT merge without review."
        )

    if label == "needs-human" then notify.notify(s"harness: #$issue needs-human ($kindText, gate $gateStatus)")

    git.commit(
      s"""feat(US-$issue): autonomous iteration — $commitTag
         |
         |Refs #$issue. Loop iteration $n, $pass gate pass(es). Outcome: $outcomeText.
         |This commit was produced by an unattended claude -p iteration (harness v2).
         |
         |Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>""".stripMargin
    )
    git.push(branch)

    val prBody = StringBuilder()
    prBody ++= s"Autonomous harness (v2) iteration $n for #$issue.\n\n"
    prBody ++= s"$prNote\n\n"
    if reviewed then
      prBody ++= s"<details><summary>Independent reviewer output</summary>\n\n```\n${fs.read(reviewFile)}\n```\n\n</details>\n\n"
    if outcome.contains(Outcome.Success) && isClass1 then
      prBody ++= "v4 auto-merge: class-1 + reviewer APPROVE — the loop merges once the required CI check is green.\n\n"
    else prBody ++= "Not auto-merged (v4 merges class-1 + APPROVE only): a human reviews and merges.\n\n"
    prBody ++= s"Closes #$issue\n"
    fs.write(s"$LogDir/issue-$issue.pr-body.md", prBody.toString)

    val prUrl = gh.createPr(branch, s"US-$issue: autonomous iteration ($outcomeText, gate $gateStatus)", prBody.toString)
    val prNum = prUrl.split('/').lastOption.flatMap(_.toIntOption) match
      case None    => Raise.raise(InfraFault("could not determine PR number from gh pr create output"))
      case Some(p) => p
    emit(cur, "PR", "ok", detail = s"pr=$prNum outcome=$outcomeText")

    if outcome.contains(Outcome.Success) && isClass1 then autoMerge(issue, prNum, cur)
    else
      gh.editLabels(issue, add = List(label), remove = List("in-progress"))
      if outcome.contains(Outcome.Success) then LoopExit.Success else LoopExit.NeedsHuman

  /** v4 auto-merge (class-1 + APPROVE only): wait-appear -> watch -> merge -> VERIFY the PR
    * state is MERGED (unverified = infra fault) -> drop in-progress -> flip blocked ->
    * fetch -> notify. CI red after green local gates = needs-human WITHOUT self-repair: the
    * loop never repairs against the independent check.
    */
  private def autoMerge(issue: Int, prNum: Int, cur: Cursor)(using
      cfg: Config,
      gh: GitHub,
      git: Git,
      gates: GateRunner,
      log: StatusLog,
      notify: Notify,
      clock: Clock
  )(using Raise[InfraFault]): LoopExit =
    val ciLog = s"$LogDir/issue-$issue.ci-wait.log"
    emit(cur, "CI_WAIT", "start", ciLog)
    // Discriminate on data, not on the exit code: a fresh PR routinely reports zero checks
    // for a few seconds (push races the workflow scheduler, PR #28 / issue #26). Block until
    // the rollup is non-empty, and only then let the CI watch judge. A check that never
    // registers is a scheduler/infra problem, never rc 40.
    if !waitForChecks(prNum) then
      Raise.raise(
        InfraFault(s"no CI check registered on PR #$prNum within ${cfg.ciAppearTimeout}s — PR open, issue stays in-progress")
      )
    gates.run("CI-WAIT", s"gh pr checks $prNum --watch --fail-fast", cfg.ciWaitTimeout, ciLog) match
      case GateResult.Timeout =>
        Raise.raise(InfraFault(s"CI wait hit the ${cfg.ciWaitTimeout}s bound — PR open, issue stays in-progress"))
      case GateResult.Red =>
        emit(cur, "CI_WAIT", "red", ciLog)
        gh.prComment(
          prNum,
          "CI red after local gates were green. The loop never self-repairs against the independent check (v3 hands-off rule) — a human must look."
        )
        gh.editLabels(issue, add = List("needs-human"), remove = List("in-progress"))
        notify.notify(s"harness: #$issue CI RED -> needs-human (PR #$prNum)")
        LoopExit.NeedsHuman
      case GateResult.Green =>
        emit(cur, "CI_WAIT", "ok", ciLog)
        emit(cur, "MERGE", "start")
        if !gh.merge(prNum) then Raise.raise(InfraFault("merge command failed — infra fault"))
        val state = gh.prState(prNum)
        if state != "MERGED" then Raise.raise(InfraFault(s"merge NOT verified (PR state '$state') — infra fault"))
        emit(cur, "MERGE", "ok", detail = s"pr=$prNum")
        gh.editLabels(issue, add = Nil, remove = List("in-progress"))
        flipBlocked(issue)
        git.fetchOriginMain() // a post-merge fetch failure is tolerated: next tick re-fetches
        notify.notify(s"harness: #$issue auto-merged (PR #$prNum, CI green, reviewer APPROVE)")
        LoopExit.Success

  /** Poll the rollup length until > 0, bounded by ciAppearTimeout. True once >=1 check is
    * registered, false on timeout.
    */
  private def waitForChecks(prNum: Int)(using cfg: Config, gh: GitHub, clock: Clock): Boolean =
    var waited = 0
    while waited < cfg.ciAppearTimeout do
      gh.checksRollupCount(prNum) match
        case Some(n) if n > 0 => return true
        case _                => ()
      clock.sleepSeconds(cfg.ciAppearInterval)
      waited += cfg.ciAppearInterval
    false

  /** `Blocked-by: #N` references in an issue body. */
  private[harness] def parseBlockedBy(body: String): List[Int] =
    "Blocked-by: #(\\d+)".r.findAllMatchIn(body).map(_.group(1).toInt).toList

  /** After a verified merge, flip every open `blocked` issue whose Blocked-by refs are ALL
    * closed. The just-merged issue counts as closed even if GitHub's async close lags the
    * merge. Issues without the sentinel are left alone (human-managed).
    */
  private def flipBlocked(mergedIssue: Int)(using gh: GitHub): Unit =
    gh.openBlockedIssues().foreach { b =>
      val refs = parseBlockedBy(gh.issueBody(b))
      if refs.nonEmpty then
        val allClosed = refs.forall(r => r == mergedIssue || gh.issueState(r) == "CLOSED")
        if allClosed then gh.editLabels(b, add = List("ready"), remove = List("blocked"))
    }

  private enum Outcome:
    case Success, Fail

  private def verdictText(v: Verdict): String = v match
    case Verdict.Approve        => "APPROVE"
    case Verdict.RequestChanges => "REQUEST_CHANGES"

  /** Last `VERDICT: (APPROVE|REQUEST_CHANGES)` occurrence wins (grep | tail -1). */
  private[harness] def parseVerdict(review: String): Option[Verdict] =
    "VERDICT: (APPROVE|REQUEST_CHANGES)".r
      .findAllMatchIn(review)
      .toList
      .lastOption
      .map(m => if m.group(1) == "APPROVE" then Verdict.Approve else Verdict.RequestChanges)

  /** The patch seam: dispatch the agent, reset to the pristine base, inspect the patch, THEN
    * apply it. The tree the agent edited is data to inspect, never trusted.
    */
  private def stagePatch(role: Role, promptFile: String, patchOut: String, currentPatch: Option[String])(using
      cfg: Config,
      git: Git,
      agents: AgentDispatch,
      fs: HarnessFs
  ): StageResult =
    agents.worker(role, promptFile, patchOut, currentPatch) match
      case DispatchOutcome.TimedOut => return StageResult.Timeout
      case DispatchOutcome.Done     => ()
    // Reset to the pristine base BEFORE looking at the patch.
    git.resetHardCleanToOriginMain()
    if fs.sizeBytes(patchOut) == 0 then return StageResult.Empty
    // Inspect, THEN apply. Fail-open is DELIBERATE and backstopped: an unparseable patch
    // yields an empty numstat (guard passes) but `git apply --index` then refuses it, so a
    // malformed patch never reaches the gates (ApplyFail = infra fault, no budget).
    val numstat = git.applyNumstat(patchOut)
    val bytes   = fs.sizeBytes(patchOut)
    if bytes > cfg.maxPatchBytes then ???
    if touchesProtected(numstat) then ???
    if !git.applyIndex(patchOut) then return StageResult.ApplyFail
    StageResult.Ok(patchOut)

  private[harness] def numstatPaths(numstat: String): List[String] =
    numstat.linesIterator.toList.flatMap { line =>
      line.split('\t') match
        case Array(_, _, p) if p.nonEmpty => Some(p)
        case _                            => None
    }

  /** The three classes the sandbox must never let an agent rewrite (CI workflows, harness
    * code, the constitution) plus docs/ and the control files.
    */
  private[harness] def touchesProtected(numstat: String): Boolean =
    numstatPaths(numstat).exists { p =>
      p.startsWith(".github/") || p.startsWith("harness/") || p.startsWith("docs/") ||
      p == "CONTEXT.md" || p == "PROMPT.md" || p == "STOP.md"
    }

  /** Test-tamper report over the applied patch's numstat, filtered to src/test and src/it. */
  private[harness] def tamperReport(numstat: String): String =
    val rows = numstat.linesIterator.toList.filter { line =>
      line.split('\t') match
        case Array(_, _, p) => p.startsWith("src/test/") || p.startsWith("src/it/")
        case _              => false
    }
    val touched = rows.size
    val netDel = rows.count { line =>
      line.split('\t') match
        case Array(a, d, _) if a != "-" && d != "-" => d.toInt > a.toInt
        case _                                      => false
    }
    val raw =
      if rows.nonEmpty then s"```\n${rows.mkString("\n")}\n```"
      else "(no test files changed vs origin/main)"
    s"""# Test-tamper report (git apply --numstat on the applied patch, filtered to src/test, src/it)
       |
       |**Summary: $touched test file(s) touched, $netDel with net deletions.**
       |
       |Raw numstat (added  deleted  path; a deleted file shows all lines as deletions):
       |
       |$raw
       |""".stripMargin
