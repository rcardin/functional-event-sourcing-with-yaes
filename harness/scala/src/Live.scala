package harness

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Production capability handlers: no yaes Effect machinery, just plain trait implementations
  * over `java.nio.file` and `scala.sys.process` / `java.lang.ProcessBuilder`, matching
  * `harness/loop.sh` byte-for-byte where the bash reference is explicit. Task 1 built the four
  * handlers that need no `gh`/`git` subprocess work: `HarnessFs`, `StatusLog`, `Notify`, `Clock`.
  * This task (slice 2, part B) adds `GitHub`, `Git`, `AgentDispatch`, `GateRunner` to the same
  * file, plus the shared `LiveProc` subprocess helper they all build on.
  */

/** loop.sh's shared `log()` helper (loop.sh:141), reused by every Live handler that needs to
  * log (Notify today; GitHub/Git/AgentDispatch/GateRunner in task 2). Format is
  * `[loop HH:MM:SS] <msg>`, written to stderr, verbatim parity, since the slice 3 parity
  * oracle greps log lines.
  */
object LiveLog:
  private val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

  def log(msg: String): Unit =
    val ts = java.time.LocalTime.now().format(fmt)
    System.err.println(s"[loop $ts] $msg")

/** Filesystem the harness owns: prompts, logs, markers, STOP.md. All paths passed into `write`/
  * `read`/`sizeBytes` arrive repo-relative from Machine (e.g. `harness/logs/issue-999.prompt.txt`)
  * and are resolved against `root`; `root` is a constructor parameter (never hardcoded to the
  * process cwd) so tests can point it at a temp dir.
  */
final class LiveHarnessFs(root: Path) extends HarnessFs:

  /** loop.sh:623 checks `$REPO_ROOT/STOP.md`, i.e. repo-root-relative, not under harness/. */
  def stopRequested(): Boolean =
    Files.exists(root.resolve("STOP.md"))

  /** loop.sh:116-118: ITERATE_PROMPT/FIX_PROMPT/REVIEW_PROMPT are `$SCRIPT_DIR/<name>.md`,
    * i.e. `harness/<name>.md` relative to the repo root (`$SCRIPT_DIR` is the harness dir).
    */
  def readTemplate(t: Template): String =
    val name = t match
      case Template.Iterate => "iterate-prompt.md"
      case Template.Fix     => "fix-prompt.md"
      case Template.Review  => "review-prompt.md"
    readString(root.resolve("harness").resolve(name))

  /** loop.sh:119: `CONVENTIONS="$REPO_ROOT/CONTEXT.md"`. */
  def conventions(): String =
    readString(root.resolve("CONTEXT.md"))

  def write(path: String, content: String): Unit =
    val p = root.resolve(path)
    Option(p.getParent).foreach(Files.createDirectories(_))
    Files.write(
      p,
      content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    ()

  def read(path: String): String =
    readString(root.resolve(path))

  def sizeBytes(path: String): Long =
    Files.size(root.resolve(path))

  private def readString(p: Path): String =
    new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8)

/** status.jsonl appender (loop.sh:151-171, `phase()`). One JSON line per event, appended to
  * `root/harness/logs/status.jsonl`. Fire and forget: the whole write path swallows exceptions,
  * matching bash's `>>"$STATUS_FILE" 2>/dev/null || true`, a wrong/missing event is a wrong
  * banner, never a wrong merge.
  */
final class LiveStatusLog(root: Path, runId: String) extends StatusLog:

  private val statusFile = root.resolve("harness").resolve("logs").resolve("status.jsonl")

  def append(event: StatusEvent): Unit =
    try
      val ts = System.currentTimeMillis() / 1000
      val pid = ProcessHandle.current().pid()
      // Repo-relative logfile normalization (loop.sh:164, the single choke point): strip a
      // leading "root/" prefix if present; empty stays empty; a foreign absolute path passes
      // through unchanged. Machine already passes repo-relative paths, so this is normally a
      // no-op, kept here to match bash's own belt-and-suspenders choke point.
      val rootPrefix = root.toString + java.io.File.separator
      val logfile =
        if event.logfile.startsWith(rootPrefix) then event.logfile.substring(rootPrefix.length)
        else event.logfile
      // Detail sanitization (loop.sh:167): strip all backslashes, strip all double quotes,
      // replace every newline with a single space, in that exact order. Machine already
      // sanitizes detail before constructing the event (Machine.sanitizeDetail); re-applying
      // here is idempotent and mirrors bash's single choke point inside phase() itself.
      val detail = event.detail.replace("\\", "").replace("\"", "").replace("\n", " ")
      val line =
        s"""{"ts":$ts,"pid":$pid,"run":"$runId","iter":${event.iter},"issue":"${event.issue}","phase":"${event.phase}","state":"${event.state}","pass":${event.pass},"budget":${event.budget},"logfile":"$logfile","detail":"$detail"}""" + "\n"
      Option(statusFile.getParent).foreach(Files.createDirectories(_))
      Files.write(
        statusFile,
        line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
      )
      ()
    catch case NonFatal(_) => ()

/** Notify seam (loop.sh:373-382). Three-way branch, every branch swallows failure: a dead
  * notification channel must never change loop behavior.
  */
final class LiveNotify(notifyCmd: Option[String], ntfyTopic: Option[String], log: String => Unit)
    extends Notify:

  def notify(msg: String): Unit =
    (notifyCmd, ntfyTopic) match
      case (Some(cmd), _) if cmd.nonEmpty =>
        try
          val pb = new ProcessBuilder("bash", "-c", cmd)
          pb.environment().put("msg", msg)
          pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
          pb.redirectError(ProcessBuilder.Redirect.INHERIT)
          val rc = pb.start().waitFor()
          if rc != 0 then log("notify failed (ignored)")
        catch case NonFatal(_) => log("notify failed (ignored)")
      case (_, Some(topic)) if topic.nonEmpty =>
        try
          val pb = new ProcessBuilder(
            "curl",
            "-s",
            "--max-time",
            "10",
            "-d",
            msg,
            s"https://ntfy.sh/$topic"
          )
          pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
          pb.redirectError(ProcessBuilder.Redirect.DISCARD)
          val rc = pb.start().waitFor()
          if rc != 0 then log("notify failed (ignored)")
        catch case NonFatal(_) => log("notify failed (ignored)")
      case _ =>
        log(s"notify (no channel configured): $msg")

/** Wall-clock waits (CI-appear poll). Interrupt handling: none, matching bash `sleep`. */
object LiveClock extends Clock:
  def sleepSeconds(s: Int): Unit =
    Thread.sleep(s * 1000L)

/** Shared subprocess-execution helper for the `gh`/`git` handlers below. Captures stdout/stderr
  * as raw bytes on two drain threads (never line-reconstructed, so diff/patch text stays
  * byte-exact) to avoid the classic deadlock where a child blocks on a full pipe nobody is
  * reading. `pathPrepend` is the test seam mirroring the bash suite's FAKEBIN mechanism (a
  * directory prepended onto PATH so a fake `gh`/`git` on PATH is picked up); production callers
  * never set it, so PATH is always exactly the inherited parent PATH, never scrubbed.
  */
private[harness] object LiveProc:

  final case class Result(rc: Int, stdout: String, stderr: String):
    /** Bash `$(...)` command-substitution semantics: strip ALL trailing newlines, nothing else
      * (not other whitespace, not leading/internal newlines).
      */
    def stdoutTrimmedTrailingNewlines: String = stdout.replaceAll("\n+$", "")

  def run(
      cwd: Path,
      args: Seq[String],
      env: Map[String, String] = Map.empty,
      pathPrepend: Option[String] = None
  ): Result =
    // NOTE: modifying the child's PATH env var alone is NOT enough to make an unqualified
    // command (e.g. "gh") resolve against a prepended test-fixture directory: on this JDK,
    // ProcessBuilder's own executable lookup for an unqualified command name uses the JVM's
    // OWN inherited PATH (a documented posix_spawn-launch-mechanism quirk), not the
    // ProcessBuilder's `environment()` map: confirmed empirically while writing this task's
    // tests. So when `pathPrepend` names a directory containing the command, resolve it to an
    // absolute path here and use THAT as argv[0]; the PATH env var is still set (belt and
    // suspenders, and correct for anything the child itself spawns via fork+exec, e.g. a
    // `bash -c` seam command that shells out further).
    val resolvedArgs = pathPrepend match
      case Some(dir) if args.nonEmpty =>
        val candidate = Path.of(dir, args.head)
        if Files.isExecutable(candidate) then candidate.toString +: args.tail else args
      case _ => args
    val pb = new ProcessBuilder(resolvedArgs.asJava)
    pb.directory(cwd.toFile)
    val procEnv = pb.environment() // inherits the parent process environment; never cleared
    pathPrepend.foreach { p =>
      val cur = Option(procEnv.get("PATH")).getOrElse("")
      procEnv.put("PATH", if cur.isEmpty then p else s"$p${java.io.File.pathSeparator}$cur")
    }
    env.foreach { case (k, v) => procEnv.put(k, v) }
    val proc = pb.start()
    // Deliberate and inert: no harness child (`git`/`gh`/GATE_CMD/MERGE_CMD/the worker or
    // reviewer stub) ever reads stdin, so closing it immediately is a no-op for all of them.
    proc.getOutputStream.close()
    val outBaos = new java.io.ByteArrayOutputStream()
    val errBaos = new java.io.ByteArrayOutputStream()
    val outT    = new Thread(() => { proc.getInputStream.transferTo(outBaos); () })
    val errT    = new Thread(() => { proc.getErrorStream.transferTo(errBaos); () })
    outT.start(); errT.start()
    val rc = proc.waitFor()
    outT.join(); errT.join()
    Result(
      rc,
      new String(outBaos.toByteArray, StandardCharsets.UTF_8),
      new String(errBaos.toByteArray, StandardCharsets.UTF_8)
    )

/** run_gate (loop.sh:242-250): a tier command under a timeout, log captured. `timeoutBin` is
  * resolved once by Main (`command -v timeout || command -v gtimeout`, verified loop.sh:174) and
  * passed in; when `None` the gate runs unbounded, matching bash's own fallback when neither
  * binary is on PATH. `cmd` is WORD-SPLIT, never `eval`'d (GATE_CMD/MERGE_CMD class).
  */
final class LiveGateRunner(root: Path, timeoutBin: Option[String]) extends GateRunner:

  def run(label: String, cmd: String, timeoutSec: Int, logFile: String): GateResult =
    LiveLog.log(s"$label gate: $cmd (timeout ${timeoutSec}s) -> $logFile")
    val words = cmd.trim.split("\\s+").toSeq
    val fullArgs = timeoutBin match
      case Some(tb) => tb +: timeoutSec.toString +: words
      case None     => words
    val logPath = root.resolve(logFile)
    Option(logPath.getParent).foreach(Files.createDirectories(_))
    val pb = new ProcessBuilder(fullArgs.asJava)
    pb.directory(root.toFile)
    pb.redirectOutput(logPath.toFile) // truncates, matches ">logfile"
    pb.redirectErrorStream(true)      // merges child stderr into the same stream, matches "2>&1"
    val rc = pb.start().waitFor()
    rc match
      case 0   => GateResult.Green
      case 124 => GateResult.Timeout
      case _   => GateResult.Red

/** dispatch_worker (loop.sh:268-297) / dispatch_review (loop.sh:311-334).
  *
  * One deliberate deviation from a byte-exact bash port, flagged prominently here and in the
  * slice-2 task report: the `REVIEW_CMD` stub path ALWAYS returns `Done`, regardless of the
  * child's own exit code. This is bash's OWN asymmetry with the worker stub path (loop.sh:
  * 314-317: a review stub cannot simulate a reviewer timeout at all in the current suite), not
  * something this port introduced. Preserved verbatim, not "fixed" into worker-like rc-124
  * propagation.
  */
final class LiveAgentDispatch(
    root: Path,
    timeoutBin: Option[String],
    iterTimeout: Int,
    implCmd: Option[String],
    fixCmd: Option[String],
    reviewCmd: Option[String]
) extends AgentDispatch:

  def worker(role: Role, promptFile: String, patchOut: String, logFile: String, currentPatch: Option[String]): DispatchOutcome =
    val overrideCmd = role match
      case Role.IMPL => implCmd
      case Role.FIX  => fixCmd
    val patchOutAbs = root.resolve(patchOut)
    val logPath     = root.resolve(logFile)
    LiveLog.log(s"dispatching $role agent -> $logPath (patch -> $patchOutAbs)")
    Option(logPath.getParent).foreach(Files.createDirectories(_))
    overrideCmd match
      case Some(cmd) if cmd.nonEmpty =>
        // Eval'd through a shell (IMPL_CMD/FIX_CMD class), PATCH_OUT exported to the child.
        val pb = new ProcessBuilder(Seq("bash", "-c", cmd).asJava)
        pb.directory(root.toFile)
        pb.environment().put("PATCH_OUT", patchOutAbs.toString)
        pb.redirectOutput(logPath.toFile)
        pb.redirectErrorStream(true)
        val rc = pb.start().waitFor()
        LiveLog.log(s"$role stub exited rc=$rc")
        if rc == 124 then DispatchOutcome.TimedOut else DispatchOutcome.Done
      case _ =>
        // Real path: harness/sandbox/run-agent.sh PROMPT_FILE PATCH_OUT [CURRENT_PATCH].
        val runner         = root.resolve("harness/sandbox/run-agent.sh").toString
        val promptAbs      = root.resolve(promptFile).toString
        val currentPatchArg = currentPatch.map(root.resolve(_).toString).getOrElse("")
        val args = (timeoutBin match
          case Some(tb) => Seq(tb, iterTimeout.toString)
          case None     => Seq.empty
        ) ++ Seq(runner, promptAbs, patchOutAbs.toString, currentPatchArg)
        val pb = new ProcessBuilder(args.asJava)
        pb.directory(root.toFile)
        pb.redirectOutput(logPath.toFile)
        pb.redirectErrorStream(true)
        val rc = pb.start().waitFor()
        if rc == 124 then
          LiveLog.log(
            s"WARNING: $role sandbox dispatch failed rc=124 (${iterTimeout}s timeout or infra fault: missing image/proxy/Docker/API key/prior-patch)"
          )
          DispatchOutcome.TimedOut
        else
          LiveLog.log(s"$role sandbox dispatch exited rc=$rc (patch written by the container)")
          DispatchOutcome.Done

  def review(prompt: String, reviewFile: String): DispatchOutcome =
    LiveLog.log(s"dispatching REVIEWER in the sandbox (cold, zero mounts, no mutating tools) -> $reviewFile")
    val reviewPath = root.resolve(reviewFile)
    val stderrPath = root.resolve(s"$reviewFile.stderr")
    Option(reviewPath.getParent).foreach(Files.createDirectories(_))
    reviewCmd match
      case Some(cmd) if cmd.nonEmpty =>
        val pb = new ProcessBuilder(Seq("bash", "-c", cmd).asJava)
        pb.directory(root.toFile)
        pb.redirectOutput(reviewPath.toFile)
        pb.redirectError(stderrPath.toFile)
        val rc = pb.start().waitFor()
        LiveLog.log(s"REVIEWER stub exited rc=$rc")
        DispatchOutcome.Done // bash asymmetry: the stub path never reads back rc==124
      case _ =>
        // Real path: REVIEW_PROMPT env carries the prompt text, never argv.
        val runner = root.resolve("harness/sandbox/run-reviewer.sh").toString
        val args = (timeoutBin match
          case Some(tb) => Seq(tb, iterTimeout.toString)
          case None     => Seq.empty
        ) :+ runner
        val pb = new ProcessBuilder(args.asJava)
        pb.directory(root.toFile)
        pb.environment().put("REVIEW_PROMPT", prompt)
        pb.redirectOutput(reviewPath.toFile)
        pb.redirectError(stderrPath.toFile)
        val rc = pb.start().waitFor()
        if rc == 124 then
          LiveLog.log(
            s"WARNING: REVIEWER sandbox dispatch failed rc=124 (${iterTimeout}s timeout or infra fault: missing image/proxy/Docker/API key)"
          )
          DispatchOutcome.TimedOut
        else
          LiveLog.log(s"REVIEWER sandbox dispatch exited rc=$rc")
          DispatchOutcome.Done

/** `git` operations (loop.sh's scattered git calls across run_gate/stage_patch/iterate/
  * auto_merge/flip_blocked). Every method runs with cwd=root; parity source is loop.sh, line
  * numbers cited per method below.
  */
final class LiveGit(root: Path) extends Git:

  private def git(args: String*): LiveProc.Result = LiveProc.run(root, "git" +: args)

  /** loop.sh:660: `[[ -z "$(git status --porcelain)" ]]`. */
  def statusClean(): Boolean =
    git("status", "--porcelain").stdout.isEmpty

  /** loop.sh:664: `git fetch --quiet origin main`. */
  def fetchOriginMain(): Boolean =
    git("fetch", "--quiet", "origin", "main").rc == 0

  /** loop.sh:666-671. */
  def checkoutBranch(branch: String): Boolean =
    if git("show-ref", "--verify", "--quiet", s"refs/heads/$branch").rc == 0 then
      LiveLog.log(s"branch $branch exists — checking it out")
      git("checkout", "--quiet", branch).rc == 0
    else
      git("checkout", "--quiet", "-b", branch, "origin/main").rc == 0

  /** loop.sh:545-546 (inside stage_patch): `git reset -q --hard origin/main` then
    * `git clean -qfd`. Bash never checks either exit code; neither does this port.
    */
  def resetHardCleanToOriginMain(): Unit =
    git("reset", "-q", "--hard", "origin/main")
    git("clean", "-qfd")
    ()

  /** loop.sh:554 (and the tamper-report twin at loop.sh:346): `git apply --numstat PATCH
    * 2>/dev/null`, fail-open (empty on any failure, never thrown).
    *
    * DEVIATION FROM THE TASK BRIEF: the brief described `patch` as text to materialize into a
    * temp file first. It is not: `patch` is a repo-relative PATH to a patch file ALREADY on
    * disk. Evidence: Machine.scala's only call sites (`git.applyNumstat(patchOut)` /
    * `git.applyIndex(patchOut)`, Machine.scala:531,542) both pass the very same `patchOut`
    * string that was just handed to `AgentDispatch.worker` as the file the agent/stub writes
    * its patch to, and bash's own `$patch`/`$patch_out` variable is, likewise, always a path
    * (the worker/fixer writes directly to `$PATCH_OUT`; there is no separate "patch content"
    * variable anywhere in loop.sh). loop.sh wins per this task's own precedence rule, so this
    * method resolves `patch` as a path (cwd=root makes the relative form work directly) instead
    * of writing a temp file.
    */
  def applyNumstat(patch: String): String =
    val r = git("apply", "--numstat", patch)
    if r.rc == 0 then r.stdoutTrimmedTrailingNewlines else ""

  /** loop.sh:568: `git apply --index PATCH >"$patch.apply.err" 2>&1` (unconditional: the
    * redirect runs whether the apply succeeds or fails, combined stdout+stderr). `patch` is
    * already a path (see `applyNumstat`'s docstring), so `<patch>.apply.err` is derivable
    * directly from it, same as bash's own `$patch.apply.err`.
    */
  def applyIndex(patch: String): Boolean =
    val errPath = root.resolve(s"$patch.apply.err")
    Option(errPath.getParent).foreach(Files.createDirectories(_))
    val pb = new ProcessBuilder(Seq("git", "apply", "--index", patch).asJava)
    pb.directory(root.toFile)
    pb.redirectOutput(errPath.toFile) // truncates, matches ">$patch.apply.err"
    pb.redirectErrorStream(true)      // merges child stderr into the same stream, matches "2>&1"
    pb.start().waitFor() == 0

  /** loop.sh:524,841: `git add PATH` (the PATCH-REJECTED.md / FIX-EMPTY.md marker paths). */
  def add(path: String): Unit =
    git("add", path)
    ()

  /** loop.sh:725,843: `git add -A`. */
  def addAll(): Unit =
    git("add", "-A")
    ()

  /** loop.sh:772: `git diff --cached origin/main` (the reviewer's diff / PR body detail). */
  def diffCachedOriginMain(): String =
    git("diff", "--cached", "origin/main").stdout

  /** loop.sh:844: `git diff --cached --quiet HEAD` (exit 0 = nothing staged),, matching Caps'
    * own docstring `"! git diff --cached --quiet HEAD"`.
    */
  def anythingStaged(): Boolean =
    git("diff", "--cached", "--quiet", "HEAD").rc != 0

  /** loop.sh:876: `git commit --quiet -m MESSAGE`. Bash's message is itself a multi-line string
    * baked into one `-m` argument (no shell re-parses it, so embedded newlines are safe as a
    * single argv element), same here, no `-F` temp file needed.
    */
  def commit(message: String): Unit =
    git("commit", "--quiet", "-m", message)
    ()

  /** loop.sh:883: `git push --quiet -u origin BRANCH`. */
  def push(branch: String): Unit =
    git("push", "--quiet", "-u", "origin", branch)
    ()

/** `gh` operations. Every method runs with cwd=root; `extraPath`, when set, is prepended onto
  * the child's PATH ahead of the inherited parent PATH: the test seam mirroring the bash
  * suite's FAKEBIN mechanism (a fake `gh` script on PATH, statemachine-test.sh:80-114).
  * Production callers leave it `None`, so PATH is always exactly the inherited parent PATH.
  */
final class LiveGitHub(
    root: Path,
    ciAppearCmd: Option[String],
    mergeCmd: Option[String],
    extraPath: Option[String] = None
) extends GitHub:

  private def gh(args: String*): LiveProc.Result = LiveProc.run(root, "gh" +: args, pathPrepend = extraPath)

  /** loop.sh:627: `gh issue list --state open --label in-progress --json number --jq .[0].number`. */
  def inProgressIssue(): Option[Int] =
    gh("issue", "list", "--state", "open", "--label", "in-progress", "--json", "number", "--jq", ".[0].number")
      .stdoutTrimmedTrailingNewlines
      .toIntOption

  /** loop.sh:629-630; the whole jq program is ONE argv element. */
  def oldestReadyIssue(): Option[Int] =
    gh(
      "issue",
      "list",
      "--state",
      "open",
      "--label",
      "ready",
      "--json",
      "number,createdAt",
      "--jq",
      "sort_by(.createdAt) | .[0].number"
    ).stdoutTrimmedTrailingNewlines.toIntOption

  /** loop.sh:643-644. */
  def issueTitleAndBody(issue: Int): String =
    gh(
      "issue",
      "view",
      issue.toString,
      "--json",
      "title,body",
      "--jq",
      "\"# \" + (.title) + \"\\n\\n\" + .body"
    ).stdout

  /** loop.sh:395 (flip_blocked's dependency-body scan). */
  def issueBody(issue: Int): String =
    gh("issue", "view", issue.toString, "--json", "body", "--jq", ".body").stdout

  /** loop.sh:650, minus the join/split round-trip (a bash string-matching artifact only,
    * Machine does `.contains("class-1")` on the `List` form directly, Machine.scala:108).
    */
  def issueLabels(issue: Int): List[String] =
    val joined = gh(
      "issue",
      "view",
      issue.toString,
      "--json",
      "labels",
      "--jq",
      "[.labels[].name] | join(\" \")"
    ).stdoutTrimmedTrailingNewlines.strip()
    if joined.isEmpty then Nil else joined.split("\\s+").toList

  /** loop.sh:401 (flip_blocked's per-reference state read). */
  def issueState(issue: Int): String =
    gh("issue", "view", issue.toString, "--json", "state", "--jq", ".state").stdoutTrimmedTrailingNewlines

  /** loop.sh:406,463,674,920: one `--add-label` per `add` element, one `--remove-label` per
    * `remove` element, order preserved (bash always passes exactly one of each; this
    * generalizes to the `List` shape `Caps` demands). Stdout discarded (bash: `>/dev/null`).
    */
  def editLabels(issue: Int, add: List[String], remove: List[String]): Boolean =
    val args = Seq("issue", "edit", issue.toString) ++
      add.flatMap(l => Seq("--add-label", l)) ++
      remove.flatMap(l => Seq("--remove-label", l))
    gh(args*).rc == 0

  /** loop.sh:392: `gh issue list --state open --label blocked --json number --jq .[].number`. */
  def openBlockedIssues(): List[Int] =
    gh("issue", "list", "--state", "open", "--label", "blocked", "--json", "number", "--jq", ".[].number").stdout.linesIterator
      .flatMap(_.strip().toIntOption)
      .toList

  /** loop.sh:903-905: body goes to a temp file first, `--body-file`, never `--body`. */
  def createPr(branch: String, title: String, body: String): String =
    val tmp = Files.createTempFile("pr-body", ".md")
    try
      Files.write(tmp, body.getBytes(StandardCharsets.UTF_8))
      gh("pr", "create", "--base", "main", "--head", branch, "--title", title, "--body-file", tmp.toString)
        .stdoutTrimmedTrailingNewlines
    finally Files.deleteIfExists(tmp)

  /** loop.sh:462: `gh pr comment PR --body BODY` (argv, not `--body-file`). */
  def prComment(pr: Int, body: String): Unit =
    gh("pr", "comment", pr.toString, "--body", body)
    ()

  /** loop.sh:479: stderr discarded, trimmed; any nonzero exit -> empty string (bash: `|| true`
    * with `2>/dev/null`).
    */
  def prState(pr: Int): String =
    val r = gh("pr", "view", pr.toString, "--json", "state", "--jq", ".state")
    if r.rc == 0 then r.stdoutTrimmedTrailingNewlines else ""

  /** loop.sh:430 (default) / eval'd CI_APPEAR_CMD seam (loop.sh:433). `pr_num` is exported into
    * the seam's `bash -c` subshell so an override that references it, as bash's own `eval`
    * would resolve it from the enclosing shell's local `$pr_num`, still resolves correctly,
    * though the current bash test suite's CI_APPEAR_CMD stubs never reference it.
    */
  def checksRollupCount(pr: Int): Option[Int] =
    val out = ciAppearCmd match
      case Some(cmd) if cmd.nonEmpty =>
        LiveProc.run(root, Seq("bash", "-c", cmd), env = Map("pr_num" -> pr.toString)).stdoutTrimmedTrailingNewlines
      case _ =>
        gh("pr", "view", pr.toString, "--json", "statusCheckRollup", "--jq", ".statusCheckRollup | length")
          .stdoutTrimmedTrailingNewlines
    if out.matches("^[0-9]+$") then out.toIntOption else None

  /** loop.sh:469,473 (default) / word-split MERGE_CMD seam (MERGE_CMD class, never `eval`'d).
    *
    * DEVIATION: bash appends the merge command's own output to the CI-wait log file
    * (`$merge_cmd >>"$ci_log" 2>&1`); `Caps.merge(pr: Int): Boolean` carries no log path for
    * this handler to append to, so the captured output is discarded here rather than silently
    * misdirected into an invented path. Flagged in the task report.
    */
  def merge(pr: Int): Boolean =
    val rc = mergeCmd match
      case Some(cmd) if cmd.nonEmpty => LiveProc.run(root, cmd.trim.split("\\s+").toSeq).rc
      case _                         => gh("pr", "merge", pr.toString, "--squash", "--delete-branch").rc
    rc == 0
