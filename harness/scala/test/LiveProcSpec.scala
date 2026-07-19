package harness

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Unit tests for the slice-2 part-B live handlers (LiveGateRunner, LiveAgentDispatch, LiveGit,
  * LiveGitHub), against the bash-parity contracts documented in Live.scala/Caps.scala. Real
  * subprocesses throughout (real `git`, a fake `gh` on PATH, tiny throwaway shell scripts),
  * temp dirs stand in for the repo root, exactly like the bash suite's per-scenario sandbox.
  */
class LiveProcSpec extends AnyFlatSpec with Matchers:

  private def tempRoot(): Path = Files.createTempDirectory("live-proc-spec")

  private def readString(p: Path): String = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)

  /** Writes an executable script (any shebang line included in `content`) and returns its path. */
  private def writeExecutable(dir: Path, name: String, content: String): Path =
    val p = dir.resolve(name)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"))
    p

  // =============================================================================================
  // LiveGateRunner
  // =============================================================================================

  "LiveGateRunner" should "return Green (rc 0) for GATE_CMD=true" in {
    val root = tempRoot()
    val gate = LiveGateRunner(root, timeoutBin = None)

    gate.run("FAST", "true", timeoutSec = 5, logFile = "harness/logs/g.log") shouldBe GateResult.Green
  }

  it should "return Red for GATE_CMD=false" in {
    val root = tempRoot()
    val gate = LiveGateRunner(root, timeoutBin = None)

    gate.run("FAST", "false", timeoutSec = 5, logFile = "harness/logs/g.log") shouldBe GateResult.Red
  }

  it should "word-split a multi-token cmd rather than run it through a shell" in {
    val root = tempRoot()
    val gate = LiveGateRunner(root, timeoutBin = None)

    // "test -f /nonexistent" only behaves correctly if split into ["test","-f","/nonexistent"]
    // and exec'd directly (never handed to a shell, which GATE_CMD/MERGE_CMD never are).
    gate.run("FAST", "test -f /nonexistent", timeoutSec = 5, logFile = "harness/logs/g.log") shouldBe GateResult.Red
  }

  it should "prepend <timeoutBin> <timeoutSec> ahead of the word-split cmd, in that order" in {
    val root = tempRoot()
    val callsFile = root.resolve("timeout-calls.log")
    val fakeTimeout = writeExecutable(
      root,
      "faketimeout.sh",
      s"""#!/usr/bin/env bash
         |printf '%s\\n' "$$*" >> "$callsFile"
         |shift
         |exec "$$@"
         |""".stripMargin
    )
    val gate = LiveGateRunner(root, timeoutBin = Some(fakeTimeout.toString))

    gate.run("FAST", "true", timeoutSec = 42, logFile = "harness/logs/g.log") shouldBe GateResult.Green

    readString(callsFile).strip() shouldBe "42 true"
  }

  it should "capture both stdout and stderr, in 2>&1 order, into the log file" in {
    val root = tempRoot()
    val script = writeExecutable(
      root,
      "both-streams.sh",
      "#!/usr/bin/env bash\necho out-line\necho err-line 1>&2\n"
    )
    val gate = LiveGateRunner(root, timeoutBin = None)

    gate.run("FAST", script.toString, timeoutSec = 5, logFile = "harness/logs/g.log") shouldBe GateResult.Green

    val logged = readString(root.resolve("harness/logs/g.log"))
    logged should include("out-line")
    logged should include("err-line")
  }

  it should "map rc 124 to Timeout" in {
    val root = tempRoot()
    val script = writeExecutable(root, "exit124.sh", "#!/usr/bin/env bash\nexit 124\n")
    val gate = LiveGateRunner(root, timeoutBin = None)

    gate.run("FAST", script.toString, timeoutSec = 5, logFile = "harness/logs/g.log") shouldBe GateResult.Timeout
  }

  // =============================================================================================
  // LiveAgentDispatch
  // =============================================================================================

  "LiveAgentDispatch.worker" should "let an IMPL_CMD override write $PATCH_OUT, landing the patch and returning Done" in {
    val root = tempRoot()
    val dispatch = LiveAgentDispatch(
      root,
      timeoutBin = None,
      iterTimeout = 5,
      implCmd = Some("echo hello patch > \"$PATCH_OUT\""),
      fixCmd = None,
      reviewCmd = None
    )

    val outcome = dispatch.worker(
      Role.IMPL,
      promptFile = "unused.txt",
      patchOut = "harness/logs/i.patch",
      logFile = "harness/logs/i.claude.log",
      currentPatch = None
    )

    outcome shouldBe DispatchOutcome.Done
    readString(root.resolve("harness/logs/i.patch")) shouldBe "hello patch\n"
  }

  it should "return TimedOut when the override stub exits 124" in {
    val root = tempRoot()
    val dispatch =
      LiveAgentDispatch(root, timeoutBin = None, iterTimeout = 5, implCmd = Some("exit 124"), fixCmd = None, reviewCmd = None)

    dispatch.worker(Role.IMPL, "unused.txt", "harness/logs/i.patch", "harness/logs/i.claude.log", None) shouldBe DispatchOutcome.TimedOut
  }

  it should "fold any non-124 exit (including nonzero) to Done" in {
    val root = tempRoot()
    val dispatch =
      LiveAgentDispatch(root, timeoutBin = None, iterTimeout = 5, implCmd = Some("exit 7"), fixCmd = None, reviewCmd = None)

    dispatch.worker(Role.IMPL, "unused.txt", "harness/logs/i.patch", "harness/logs/i.claude.log", None) shouldBe DispatchOutcome.Done
  }

  it should "select FIX_CMD for Role.FIX, independent of IMPL_CMD" in {
    val root = tempRoot()
    val dispatch = LiveAgentDispatch(
      root,
      timeoutBin = None,
      iterTimeout = 5,
      implCmd = Some("exit 1"), // would fold to Done too, but must not be the one that runs
      fixCmd = Some("echo fix patch > \"$PATCH_OUT\""),
      reviewCmd = None
    )

    dispatch.worker(Role.FIX, "unused.txt", "harness/logs/f.patch", "harness/logs/f.claude.log", None) shouldBe DispatchOutcome.Done
    readString(root.resolve("harness/logs/f.patch")) shouldBe "fix patch\n"
  }

  it should "write the worker child's combined output to the given logFile (bash parity: $logf)" in {
    val root = tempRoot()
    val dispatch = LiveAgentDispatch(
      root,
      timeoutBin = None,
      iterTimeout = 5,
      implCmd = Some("echo worker-stdout; echo worker-stderr 1>&2"),
      fixCmd = None,
      reviewCmd = None
    )

    dispatch.worker(
      Role.IMPL,
      "unused.txt",
      "harness/logs/i.patch",
      "harness/logs/issue-999-iter1.claude.log",
      None
    ) shouldBe DispatchOutcome.Done

    val logged = readString(root.resolve("harness/logs/issue-999-iter1.claude.log"))
    logged should include("worker-stdout")
    logged should include("worker-stderr")
  }

  "LiveAgentDispatch.review" should "land stdout in reviewFile and stderr in reviewFile.stderr" in {
    val root = tempRoot()
    val dispatch = LiveAgentDispatch(
      root,
      timeoutBin = None,
      iterTimeout = 5,
      implCmd = None,
      fixCmd = None,
      reviewCmd = Some("echo VERDICT: APPROVE; echo diagnostic 1>&2")
    )

    val outcome = dispatch.review("the prompt (unused by the stub)", "harness/logs/r.md")

    outcome shouldBe DispatchOutcome.Done
    readString(root.resolve("harness/logs/r.md")) should include("VERDICT: APPROVE")
    readString(root.resolve("harness/logs/r.md.stderr")) should include("diagnostic")
  }

  it should "preserve the bash asymmetry: a REVIEW_CMD stub exiting 124 still returns Done" in {
    val root = tempRoot()
    val dispatch =
      LiveAgentDispatch(root, timeoutBin = None, iterTimeout = 5, implCmd = None, fixCmd = None, reviewCmd = Some("exit 124"))

    dispatch.review("prompt", "harness/logs/r.md") shouldBe DispatchOutcome.Done
  }

  // =============================================================================================
  // LiveGit
  // =============================================================================================

  /** A bash-suite-shaped fixture: a bare "origin" plus a work clone with one commit on `main`,
    * pushed to origin, the minimum a `checkoutBranch`/`fetchOriginMain`/`applyIndex` test needs.
    */
  private def setupGitRepo(): Path =
    val bare = Files.createTempDirectory("live-git-bare")
    LiveProc.run(bare, Seq("git", "init", "--quiet", "--bare"))
    val work = Files.createTempDirectory("live-git-work")
    LiveProc.run(work, Seq("git", "init", "--quiet"))
    LiveProc.run(work, Seq("git", "config", "user.email", "t@t"))
    LiveProc.run(work, Seq("git", "config", "user.name", "t"))
    LiveProc.run(work, Seq("git", "config", "commit.gpgsign", "false"))
    Files.write(work.resolve("base.txt"), "base\n".getBytes(StandardCharsets.UTF_8))
    LiveProc.run(work, Seq("git", "add", "-A"))
    LiveProc.run(work, Seq("git", "commit", "--quiet", "-m", "init"))
    LiveProc.run(work, Seq("git", "branch", "--quiet", "-M", "main"))
    LiveProc.run(work, Seq("git", "remote", "add", "origin", bare.toString))
    LiveProc.run(work, Seq("git", "push", "--quiet", "-u", "origin", "main"))
    work

  private def newFilePatch(path: String, content: String): String =
    val lines = content.linesIterator.toList
    val body  = lines.map(l => s"+$l").mkString("\n")
    s"""diff --git a/$path b/$path
       |new file mode 100644
       |--- /dev/null
       |+++ b/$path
       |@@ -0,0 +1,${lines.size} @@
       |$body
       |""".stripMargin

  "LiveGit.statusClean" should "be true on a freshly cloned tree and false once a file is dirtied" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    git.statusClean() shouldBe true

    Files.write(work.resolve("untracked.txt"), "x".getBytes(StandardCharsets.UTF_8))
    git.statusClean() shouldBe false
  }

  "LiveGit.fetchOriginMain / push" should "round-trip against the bare origin" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    git.fetchOriginMain() shouldBe true

    LiveProc.run(work, Seq("git", "checkout", "--quiet", "-b", "us-1"))
    Files.write(work.resolve("feature.txt"), "feature\n".getBytes(StandardCharsets.UTF_8))
    LiveProc.run(work, Seq("git", "add", "-A"))
    LiveProc.run(work, Seq("git", "commit", "--quiet", "-m", "feature"))

    git.push("us-1")

    val originRef = LiveProc.run(work, Seq("git", "ls-remote", "origin", "refs/heads/us-1"))
    originRef.stdout should include("refs/heads/us-1")
  }

  "LiveGit.checkoutBranch" should "branch off origin/main when the branch does not exist locally, else check it out" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    git.checkoutBranch("us-2") shouldBe true
    LiveProc.run(work, Seq("git", "rev-parse", "--abbrev-ref", "HEAD")).stdoutTrimmedTrailingNewlines shouldBe "us-2"

    LiveProc.run(work, Seq("git", "checkout", "--quiet", "main"))
    git.checkoutBranch("us-2") shouldBe true // now takes the "exists" path
    LiveProc.run(work, Seq("git", "rev-parse", "--abbrev-ref", "HEAD")).stdoutTrimmedTrailingNewlines shouldBe "us-2"
  }

  "LiveGit.resetHardCleanToOriginMain" should "revert tracked edits and remove untracked files" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    Files.write(work.resolve("base.txt"), "dirtied\n".getBytes(StandardCharsets.UTF_8))
    Files.write(work.resolve("untracked.txt"), "junk".getBytes(StandardCharsets.UTF_8))

    git.resetHardCleanToOriginMain()

    readString(work.resolve("base.txt")) shouldBe "base\n"
    Files.exists(work.resolve("untracked.txt")) shouldBe false
  }

  "LiveGit.applyNumstat" should "report added/deleted/path for a valid new-file patch" in {
    val work  = setupGitRepo()
    val git   = LiveGit(work)
    val patch = newFilePatch("src/main/scala/New.scala", "object New")
    Files.write(work.resolve("new.patch"), patch.getBytes(StandardCharsets.UTF_8))

    val numstat = git.applyNumstat("new.patch")

    numstat.strip() shouldBe "1\t0\tsrc/main/scala/New.scala"
  }

  it should "fail open: return empty text for an unparseable patch, never throw" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)
    Files.write(work.resolve("garbage.patch"), "this is not a patch at all\n".getBytes(StandardCharsets.UTF_8))

    noException should be thrownBy {
      git.applyNumstat("garbage.patch") shouldBe ""
    }
  }

  "LiveGit.applyIndex" should "apply a valid patch (true) and stage it, and refuse a garbage patch (false)" in {
    val work  = setupGitRepo()
    val git   = LiveGit(work)
    val patch = newFilePatch("src/main/scala/New.scala", "object New")
    Files.write(work.resolve("new.patch"), patch.getBytes(StandardCharsets.UTF_8))

    git.applyIndex("new.patch") shouldBe true
    Files.exists(work.resolve("src/main/scala/New.scala")) shouldBe true
    git.anythingStaged() shouldBe true

    Files.write(work.resolve("garbage.patch"), "not a patch\n".getBytes(StandardCharsets.UTF_8))
    git.applyIndex("garbage.patch") shouldBe false

    // bash parity: `git apply --index PATCH >"$patch.apply.err" 2>&1`, unconditional — the file
    // lands with git's own error message even though the apply failed.
    val applyErr = readString(work.resolve("garbage.patch.apply.err"))
    applyErr should not be empty
  }

  "LiveGit add/commit/anythingStaged" should "round-trip: dirty -> staged -> committed -> clean" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    git.anythingStaged() shouldBe false

    Files.write(work.resolve("new.txt"), "new\n".getBytes(StandardCharsets.UTF_8))
    git.add("new.txt")
    git.anythingStaged() shouldBe true

    git.commit("feat: add new.txt\n\nRefs #1.")
    git.anythingStaged() shouldBe false
    git.statusClean() shouldBe true
  }

  "LiveGit.addAll / diffCachedOriginMain" should "stage everything and diff against origin/main" in {
    val work = setupGitRepo()
    val git  = LiveGit(work)

    Files.write(work.resolve("new.txt"), "new content\n".getBytes(StandardCharsets.UTF_8))
    git.addAll()

    val diff = git.diffCachedOriginMain()
    diff should include("new.txt")
    diff should include("new content")
  }

  // =============================================================================================
  // LiveGitHub
  // =============================================================================================

  /** A fake `gh` on a throwaway PATH dir, mirroring statemachine-test.sh's FAKEBIN mechanism
    * (statemachine-test.sh:80-114): case-dispatches on `$1 $2`, logs every call verbatim to
    * `$GH_CALLS`, and answers deterministically (branching on the issue/PR number in argv rather
    * than env vars, since there is no per-call env-injection hook on LiveGitHub/LiveProc; matches
    * production, which never scrubs or augments PATH's sibling env vars for `gh`).
    */
  private def setupFakeGh(): (Path, Path, Path) =
    val binDir       = Files.createTempDirectory("fake-gh-bin")
    val callsFile    = Files.createTempFile("gh-calls", ".log")
    val capturedBody = Files.createTempFile("gh-captured-body", ".md")
    writeExecutable(
      binDir,
      "gh",
      s"""#!/usr/bin/env bash
         |echo "gh $$*" >> "$callsFile"
         |case "$$1 $$2" in
         |  "issue list")
         |    if [[ "$$*" == *"--label in-progress"* ]]; then echo ""
         |    elif [[ "$$*" == *"--label ready"* ]]; then echo "999"
         |    elif [[ "$$*" == *"--label blocked"* ]]; then echo "555"
         |    fi ;;
         |  "issue view")
         |    id="$$3"
         |    if [[ "$$*" == *"--json title,body"* ]]; then
         |      printf '# US-%s sample\\n\\nAC1: implement.\\n' "$$id"
         |    elif [[ "$$*" == *"--json labels"* ]]; then
         |      echo "ready class-1"
         |    elif [[ "$$*" == *"--json body"* ]]; then
         |      echo "Blocked-by: #999"
         |    elif [[ "$$*" == *"--json state"* ]]; then
         |      echo "CLOSED"
         |    fi ;;
         |  "issue edit") : ;;
         |  "pr create")
         |    for ((i=1;i<=$$#;i++)); do
         |      if [[ "$${!i}" == "--body-file" ]]; then
         |        j=$$((i+1)); cp "$${!j}" "$capturedBody"
         |      fi
         |    done
         |    echo "https://github.com/test/test/pull/123" ;;
         |  "pr comment") : ;;
         |  "pr merge") : ;;
         |  "pr view")
         |    pr="$$3"
         |    if [[ "$$*" == *statusCheckRollup* ]]; then echo "1"
         |    elif [[ "$$pr" == "666" ]]; then exit 1
         |    else echo "MERGED"
         |    fi ;;
         |  *) : ;;
         |esac
         |""".stripMargin
    )
    (binDir, callsFile, capturedBody)

  "LiveGitHub.inProgressIssue / oldestReadyIssue" should "call the exact bash argv and parse the result" in {
    val root                        = tempRoot()
    val (binDir, callsFile, _)      = setupFakeGh()
    val gh                          = LiveGitHub(root, ciAppearCmd = None, mergeCmd = None, extraPath = Some(binDir.toString))

    gh.inProgressIssue() shouldBe None // the fake answers "" for --label in-progress
    gh.oldestReadyIssue() shouldBe Some(999)

    val calls = readString(callsFile)
    calls should include("gh issue list --state open --label in-progress --json number --jq .[0].number")
    calls should include(
      "gh issue list --state open --label ready --json number,createdAt --jq sort_by(.createdAt) | .[0].number"
    )
  }

  "LiveGitHub.issueTitleAndBody" should "pass the exact jq program as one argv element" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, None, None, extraPath = Some(binDir.toString))

    val body = gh.issueTitleAndBody(999)

    body should include("# US-999 sample")
    readString(callsFile) should include("""gh issue view 999 --json title,body --jq "# " + (.title) + "\n\n" + .body""")
  }

  "LiveGitHub.editLabels" should "build one --add-label per add element and one --remove-label per remove element" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, None, None, extraPath = Some(binDir.toString))

    gh.editLabels(999, add = List("in-progress"), remove = List("ready")) shouldBe true

    readString(callsFile) should include("gh issue edit 999 --add-label in-progress --remove-label ready")
  }

  "LiveGitHub.createPr" should "write the body to a temp file and pass it via --body-file" in {
    val root                              = tempRoot()
    val (binDir, callsFile, capturedBody) = setupFakeGh()
    val gh                                = LiveGitHub(root, None, None, extraPath = Some(binDir.toString))

    val prUrl = gh.createPr("us-999", "US-999: autonomous iteration (SUCCESS, gate GREEN)", "the full PR body\ntext")

    prUrl shouldBe "https://github.com/test/test/pull/123"
    readString(capturedBody) shouldBe "the full PR body\ntext"
    readString(callsFile) should include(
      "gh pr create --base main --head us-999 --title US-999: autonomous iteration (SUCCESS, gate GREEN) --body-file"
    )
  }

  "LiveGitHub.checksRollupCount" should "call the default gh argv when CI_APPEAR_CMD is unset" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, ciAppearCmd = None, mergeCmd = None, extraPath = Some(binDir.toString))

    gh.checksRollupCount(42) shouldBe Some(1)

    readString(callsFile) should include("gh pr view 42 --json statusCheckRollup --jq .statusCheckRollup | length")
  }

  it should "run the CI_APPEAR_CMD seam instead of calling gh at all" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, ciAppearCmd = Some("echo 3"), mergeCmd = None, extraPath = Some(binDir.toString))

    gh.checksRollupCount(42) shouldBe Some(3)

    readString(callsFile) shouldBe ""
  }

  "LiveGitHub.merge" should "call the default gh pr merge argv when MERGE_CMD is unset" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, ciAppearCmd = None, mergeCmd = None, extraPath = Some(binDir.toString))

    gh.merge(42) shouldBe true

    readString(callsFile) should include("gh pr merge 42 --squash --delete-branch")
  }

  it should "word-split and run the MERGE_CMD seam instead of calling gh at all" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, ciAppearCmd = None, mergeCmd = Some("true"), extraPath = Some(binDir.toString))

    gh.merge(42) shouldBe true

    readString(callsFile) shouldBe ""
  }

  "LiveGitHub.prState" should "return the trimmed state on success and empty string on failure" in {
    val root                   = tempRoot()
    val (binDir, callsFile, _) = setupFakeGh()
    val gh                     = LiveGitHub(root, None, None, extraPath = Some(binDir.toString))

    gh.prState(42) shouldBe "MERGED"
    gh.prState(666) shouldBe "" // the fake exits 1 for pr 666

    readString(callsFile) should include("gh pr view 42 --json state --jq .state")
  }
