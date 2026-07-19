package harness

import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal

/** Entry point: `scala-cli run harness/scala`. Env parsing, preflight, and the MAX_ITERS driver
  * loop that reproduces harness/loop.sh's outer shell (loop.sh:100-215 for startup/preflight,
  * loop.sh:925-944 for the driver) over the Machine/Live wiring tasks 1-2 built. No CLI args
  * are consumed; everything is env, exactly like bash.
  */
object Main:

  // ---- Part C: pure env parsing, testable without touching the real environment ------------

  /** Everything `Main` needs from the environment, parsed once. Mirrors loop.sh:100-139's
    * env-var defaulting block; `Config` (Domain.scala) carries the per-iteration knobs, this
    * carries the driver-level (`maxIters`) and Live-handler-only (seams, ntfyTopic,
    * gateOverridden) knobs Config has no field for.
    */
  final case class ParsedEnv(
      cfg: Config,
      maxIters: Int,
      implCmd: Option[String],
      fixCmd: Option[String],
      reviewCmd: Option[String],
      notifyCmd: Option[String],
      ciAppearCmd: Option[String],
      mergeCmd: Option[String],
      ntfyTopic: Option[String],
      gateOverridden: Boolean
  )

  def parseEnv(env: Map[String, String]): ParsedEnv =
    def str(key: String): Option[String]       = env.get(key).filter(_.nonEmpty)
    def int(key: String, default: Int): Int    = env.get(key).flatMap(_.toIntOption).getOrElse(default)
    def long(key: String, default: Long): Long = env.get(key).flatMap(_.toLongOption).getOrElse(default)

    // loop.sh:129-133: GATE_OVERRIDDEN is captured BEFORE GATE_CMD's own default is applied, so
    // setting GATE_CMD to its own default value still counts as "overridden".
    val gateOverridden = env.get("GATE_CMD").exists(_.nonEmpty)
    val gateCmd         = str("GATE_CMD").getOrElse(Config().gateCmd)

    val cfg = Config(
      dryRun = env.getOrElse("DRY_RUN", "0") == "1", // loop.sh:654: `[[ "$DRY_RUN" == "1" ]]`
      repairBudget = int("REPAIR_BUDGET", 2),
      maxPatchBytes = long("MAX_PATCH_BYTES", 1_000_000L),
      gateCmd = gateCmd,
      ciWaitCmd = str("CI_WAIT_CMD"),
      gateTimeout = int("GATE_TIMEOUT", 900),
      iterTimeout = int("ITER_TIMEOUT", 1800),
      ciWaitTimeout = int("CI_WAIT_TIMEOUT", 900),
      ciAppearTimeout = int("CI_APPEAR_TIMEOUT", 300),
      ciAppearInterval = int("CI_APPEAR_INTERVAL", 10)
    )

    ParsedEnv(
      cfg = cfg,
      maxIters = int("MAX_ITERS", 1),
      implCmd = str("IMPL_CMD"),
      fixCmd = str("FIX_CMD"),
      reviewCmd = str("REVIEW_CMD"),
      notifyCmd = str("NOTIFY_CMD"),
      ciAppearCmd = str("CI_APPEAR_CMD"),
      mergeCmd = str("MERGE_CMD"),
      ntfyTopic = str("NTFY_TOPIC"),
      gateOverridden = gateOverridden
    )

  // ---- `command -v` equivalent ---------------------------------------------------------------

  /** Scans `pathEnv` (colon-separated, like `$PATH`) for an executable named `name`; `exists` is
    * the file-executable predicate, injected so this stays testable without the real host PATH
    * or filesystem.
    */
  private[harness] def findOnPath(pathEnv: String, name: String, exists: String => Boolean): Option[String] =
    pathEnv
      .split(java.io.File.pathSeparator)
      .toList
      .filter(_.nonEmpty)
      .map(dir => s"$dir${java.io.File.separator}$name")
      .find(exists)

  private def onRealPath(pathEnv: String, name: String): Option[String] =
    findOnPath(pathEnv, name, p => Files.isExecutable(Path.of(p)))

  // ---- Part B: driver rc -> process-exit-code map (loop.sh:925-944) ------------------------

  enum DriverAction:
    case Continue
    case Exit(code: Int)

  /** loop.sh:931-943's `case` block: rc 0 (Success) and rc 40 (NeedsHuman) are the only two that
    * do NOT `exit`; the driver logs and lets the `for` loop advance to the next iteration.
    * Every other rc exits the process immediately. `LoopExit` is closed to exactly these 7
    * cases, so there is no bash `*)` passthrough branch to reproduce here.
    */
  private[harness] def driverAction(exit: LoopExit): DriverAction = exit match
    case LoopExit.Success | LoopExit.NeedsHuman                => DriverAction.Continue
    case LoopExit.ManualStop | LoopExit.Idle | LoopExit.DryRun => DriverAction.Exit(0)
    case LoopExit.NothingMade                                  => DriverAction.Exit(1)
    case LoopExit.InfraFault                                   => DriverAction.Exit(50)

  /** The exact bash log line for one iteration's outcome (loop.sh:932-941), copied byte-for-byte
    * including loop.sh's em-dash separator character. rc 50's notify already fires inside
    * `Machine.runOnce` (Machine.scala:69, confirmed in the task report); this function only
    * logs, it never notifies a second time.
    */
  private[harness] def driverLog(i: Int, exit: LoopExit): String = exit match
    case LoopExit.Success     => s"iteration $i done (SUCCESS — auto-merged, or PR -> needs-review)"
    case LoopExit.NeedsHuman  => s"iteration $i done (FAIL terminal -> needs-human, PR open for audit)"
    case LoopExit.ManualStop  => "manual STOP.md — exiting"
    case LoopExit.Idle        => "no actionable issue — idle, exiting"
    case LoopExit.DryRun      => "dry run reached its stop point — exiting"
    case LoopExit.NothingMade => s"iteration $i produced nothing — exiting for inspection"
    case LoopExit.InfraFault  => "infra fault — exiting for inspection (issue stays in-progress)"

  /** loop.sh:927-943: run up to `maxIters` ticks, applying the rc -> action map after each one.
    * Returns the process exit code the caller must `sys.exit` with; `sys.exit` itself stays out
    * of this function (and out of `driverAction`/`driverLog`) so the mapping logic is callable
    * and testable without terminating the JVM.
    */
  private def runDriver(maxIters: Int)(using
      Config,
      GitHub,
      Git,
      AgentDispatch,
      GateRunner,
      StatusLog,
      Notify,
      HarnessFs,
      Clock
  ): Int =
    var i = 1
    while i <= maxIters do
      val exit = Machine.runOnce(i)
      LiveLog.log(driverLog(i, exit))
      driverAction(exit) match
        case DriverAction.Continue   => i += 1
        case DriverAction.Exit(code) => return code
    LiveLog.log(s"hit MAX_ITERS=$maxIters — exiting")
    0

  // ---- fatal preflight die() (loop.sh:142: `die() { log "FATAL: $*"; exit 1; }`) -----------

  private def die(msg: String): Nothing =
    LiveLog.log(s"FATAL: $msg")
    sys.exit(1)

  /** Runs build-image.sh / start-proxy.sh with inherited stdio, cwd=root, no args, matching how
    * loop.sh invokes them (their output is not redirected in bash either).
    */
  private def runInherited(cwd: Path, script: Path): Int =
    val pb = new ProcessBuilder(script.toString)
    pb.directory(cwd.toFile)
    pb.inheritIO()
    pb.start().waitFor()

  /** Runs stop-proxy.sh with both streams discarded, matching bash's EXIT-trap invocation
    * verbatim (loop.sh:210: `"$SCRIPT_DIR/sandbox/stop-proxy.sh" >/dev/null 2>&1 || true`).
    */
  private def runDiscarded(cwd: Path, script: Path): Int =
    val pb = new ProcessBuilder(script.toString)
    pb.directory(cwd.toFile)
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    pb.redirectError(ProcessBuilder.Redirect.DISCARD)
    pb.start().waitFor()

  // ---- Part B: entry point -------------------------------------------------------------------

  @main def harnessLoop(): Unit =
    val env = sys.env

    // 1. root = cwd. loop.sh derives REPO_ROOT from its own script location (loop.sh:102-105);
    // the Scala loop has no script location of its own (it's invoked as `scala-cli run
    // harness/scala` from the repo root), so cwd stands in as the Scala equivalent of
    // REPO_ROOT until the slice-4 shim (`exec scala-cli run harness/scala -- "$@"`) pins it.
    val root = Paths.get("").toAbsolutePath

    // 2. Parse every env var (Part C), capturing gateOverridden before GATE_CMD's own default.
    val parsed = parseEnv(env)

    // 3. JAVA_HOME pin block (loop.sh:176-192). DEVIATION FROM BASH: bash exports
    // JAVA_HOME/PATH so the `sbt`/`claude` CHILD PROCESSES it forks pick up JDK 25 (yaes 0.20.0
    // needs JDK 25's StructuredTaskScope API). This Scala loop is ITSELF already a JVM process
    // (scala-cli's own `//> using jvm temurin:25` pin), spawns no host sbt/java children of its
    // own, and never plumbs JAVA_HOME into the gate/agent subprocesses it does spawn: those are
    // containerized, their JDK lives in the sandbox image, unaffected by the host JVM's
    // JAVA_HOME. So mutating this JVM's own env would be a no-op with nothing downstream to
    // observe it. Kept as a log-only check for outward env-var-surface parity (design decision
    // #4 lists JAVA_HOME_PINNED as part of the "same env vars" contract).
    val javaHomePinned =
      env.getOrElse("JAVA_HOME_PINNED", s"${env.getOrElse("HOME", "")}/.sdkman/candidates/java/25.0.2-open")
    if !Files.isExecutable(Path.of(javaHomePinned, "bin", "java")) then
      LiveLog.log(s"WARNING: pinned JDK not at $javaHomePinned — gate runs under default java (may abort)")

    // 4. mkdir -p harness/logs (loop.sh:120-121: LOG_DIR="$SCRIPT_DIR/logs"; mkdir -p "$LOG_DIR").
    Files.createDirectories(root.resolve(Machine.LogDir))

    // 5. RUN_ID = epoch seconds at startup, as a String (loop.sh:152: `RUN_ID="$(date +%s)"`).
    val runId = (System.currentTimeMillis() / 1000).toString

    val pathEnv = env.getOrElse("PATH", "")

    // 6a. gh/sbt/claude must be findable on PATH (loop.sh:195-197).
    if onRealPath(pathEnv, "gh").isEmpty then die("gh not found")
    if onRealPath(pathEnv, "sbt").isEmpty then die("sbt not found")
    if onRealPath(pathEnv, "claude").isEmpty then die("claude not found")

    // 6b. Sandbox preflight (loop.sh:198-211), skipped entirely when GATE_CMD is overridden.
    if !parsed.gateOverridden then
      if env.getOrElse("CLAUDE_CODE_OAUTH_TOKEN", "").isEmpty && env.getOrElse("ANTHROPIC_API_KEY", "").isEmpty then
        die(
          "neither CLAUDE_CODE_OAUTH_TOKEN nor ANTHROPIC_API_KEY set — the sandboxed worker/fixer has no other way to authenticate"
        )
      if runInherited(root, root.resolve("harness/sandbox/build-image.sh")) != 0 then die("sandbox image build failed")
      if runInherited(root, root.resolve("harness/sandbox/start-proxy.sh")) != 0 then die("sandbox proxy failed to start")
      // loop.sh:210's `trap ... EXIT` equivalent: fires on normal completion, any sys.exit
      // (including from a later die()), or an uncaught exception; addShutdownHook guarantees
      // this the same way bash's EXIT trap does.
      sys.addShutdownHook {
        try runDiscarded(root, root.resolve("harness/sandbox/stop-proxy.sh"))
        catch case NonFatal(_) => 0
        ()
      }

    // 6c. Prompt template / conventions file existence (loop.sh:116-119, 212-215).
    val iteratePrompt = root.resolve("harness/iterate-prompt.md")
    val fixPrompt      = root.resolve("harness/fix-prompt.md")
    val reviewPrompt   = root.resolve("harness/review-prompt.md")
    val conventions    = root.resolve("CONTEXT.md")
    for f <- List(iteratePrompt, fixPrompt, reviewPrompt) do
      if !Files.isRegularFile(f) then die(s"missing prompt template: $f")
    if !Files.isRegularFile(conventions) then die(s"missing conventions file: $conventions")

    // 7. timeoutBin = first of `timeout`, `gtimeout` found on PATH, else None (loop.sh:174).
    val timeoutBin = onRealPath(pathEnv, "timeout").orElse(onRealPath(pathEnv, "gtimeout"))

    // 8. Wire the Live handlers as a single `using` bundle for Machine.runOnce.
    given Config        = parsed.cfg
    given GitHub        = LiveGitHub(root, parsed.ciAppearCmd, parsed.mergeCmd)
    given Git           = LiveGit(root)
    given AgentDispatch =
      LiveAgentDispatch(root, timeoutBin, parsed.cfg.iterTimeout, parsed.implCmd, parsed.fixCmd, parsed.reviewCmd)
    given GateRunner = LiveGateRunner(root, timeoutBin)
    given StatusLog  = LiveStatusLog(root, runId)
    given Notify     = LiveNotify(parsed.notifyCmd, parsed.ntfyTopic, LiveLog.log)
    given HarnessFs  = LiveHarnessFs(root)
    given Clock      = LiveClock

    // 9. loop.sh:926's start line, copied byte-for-byte. DRY_RUN is rendered as the raw env
    // string bash would show (`$DRY_RUN` after its own `${DRY_RUN:-0}` default), not the parsed
    // boolean.
    LiveLog.log(
      s"v2 loop start (MAX_ITERS=${parsed.maxIters}, ITER_TIMEOUT=${parsed.cfg.iterTimeout}s, REPAIR_BUDGET=${parsed.cfg.repairBudget}, DRY_RUN=${env.getOrElse("DRY_RUN", "0")})"
    )

    sys.exit(runDriver(parsed.maxIters))
