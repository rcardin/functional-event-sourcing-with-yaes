package harness

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.control.NonFatal

/** Production capability handlers: no yaes Effect machinery, just plain trait implementations
  * over `java.nio.file` and `scala.sys.process` / `java.lang.ProcessBuilder`, matching
  * `harness/loop.sh` byte-for-byte where the bash reference is explicit. This task (slice 2,
  * part A) builds the four handlers that need no `gh`/`git` subprocess work: `HarnessFs`,
  * `StatusLog`, `Notify`, `Clock`. Task 2 adds `GitHub`, `Git`, `AgentDispatch`, `GateRunner`
  * to this same file.
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
