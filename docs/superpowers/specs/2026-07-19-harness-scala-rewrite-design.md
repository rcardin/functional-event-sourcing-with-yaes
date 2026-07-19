# Harness Scala rewrite — typed state machine over yaes capabilities

Status: designed, not implemented
Date: 2026-07-19
Builds on: harness v6 slice 4 (`harness/loop.sh`, PR #40), observability v5 (`harness/watch.sh`)

## Problem

`harness/loop.sh` is a 944-line bash state machine; the harness totals ~2600 lines of bash.
Six versions in, every new invariant (infra-fault rc 50 spends no budget, patch-guard
rejection skips the repair loop, empty-fix routes to needs-human) lands as scattered `exit`
calls, dynamic-scope locals and README prose. The test suite (`test/statemachine-test.sh`,
142 assertions) works by deception: a throwaway sandbox, a fake `gh` on PATH, env-var command
stubs. It caught real bugs, but both the machine and its tests are at the edge of what bash
can keep legible.

## Decisions (grilled 2026-07-19)

1. **Scope.** Port the state machine (`loop.sh`) and its offline test suite to Scala. The
   sandbox docker wrappers (`sandbox/run-*.sh`, proxy scripts), `watch.sh`, `banner.sh`,
   `tail-claude.sh` stay bash.
2. **Stack.** Standalone scala-cli project under `harness/scala/`, Scala 3.8.3, yaes 0.20.0
   (`yaes-core`) for capabilities and the `Raise` error channel. No cats-effect/ZIO, no sbt
   module (would couple the harness build to agent-authored `build.sbt`, which the threat
   model distrusts). Dogfooding is deliberate: the harness becomes the repo's second real
   yaes program, on ugly ground (subprocesses, timeouts, exit codes).
3. **Sandbox seam verbatim.** The Scala loop shells out to the wrappers under today's exact
   contract: `run-fast-gate.sh` (argv-free, rc 0/124/other), `run-agent.sh PROMPT_FILE
   PATCH_OUT [CURRENT_PATCH]`, `run-reviewer.sh` (prompt via `REVIEW_PROMPT` env, stdout is
   the review), `build-image.sh` / `start-proxy.sh` / `stop-proxy.sh`. rc 124 = infra fault,
   everywhere.
4. **Outward surface identical.** Same env vars (`MAX_ITERS`, `ITER_TIMEOUT`, `GATE_TIMEOUT`,
   `DRY_RUN`, `REPAIR_BUDGET`, `MAX_PATCH_BYTES`, `CI_WAIT_TIMEOUT`, `CI_APPEAR_TIMEOUT`,
   `CI_APPEAR_INTERVAL`, `JAVA_HOME_PINNED`, `NTFY_TOPIC`), same test seams (`GATE_CMD`,
   `IMPL_CMD`, `FIX_CMD`, `REVIEW_CMD`, `NOTIFY_CMD`, `CI_WAIT_CMD`, `CI_APPEAR_CMD`,
   `MERGE_CMD` — env-var command strings, stub semantics unchanged, `PATCH_OUT` exported to
   worker stubs), same exit codes (0/10/11/20/30/40/50, plus 1 for nothing-staged), same
   `status.jsonl` schema and `harness/logs/` layout. `watch.sh` and the docs keep working
   unmodified.
5. **Migration = parity oracle.** The bash suite drives the Scala loop before the swap:
   point `statemachine-test.sh` at the Scala entry, all 142 assertions green. Then DRY_RUN
   probe, then one real class-1 US end-to-end. Only then is `loop.sh` replaced.
6. **Internal shape.** Typed state machine, not a transliteration and not event-sourced.
   States and results as ADTs, transitions as pure functions where possible, all effects
   behind capabilities. The README's control-flow section should read off the code.
7. **Process.** This doc + four slice issues labelled `planned` (never `ready` /
   `ready-for-agent` — `harness/` is a protected path; the loop must never pick these up).
   Built interactively, one PR per slice; the bash harness stays operational on main until
   the slice-4 swap PR.

## Architecture

```
harness/scala/
  project.scala            // scala-cli directives: scala 3.8.3, yaes-core 0.20.0, jvm 25
  src/
    Domain.scala           // ADTs: LoopExit, StageResult, GateResult, Verdict, FailureKind, ...
    Machine.scala          // iterate(): the state machine, pure decisions over capabilities
    Caps.scala             // capability traits (below)
    Live.scala             // production handlers: subprocess, fs, gh, git
    Main.scala             // driver: env parsing, preflight, MAX_ITERS loop, exit-code map
  test/
    ScenarioSpec.scala     // ported scenarios A–T over in-memory handlers
    Recorder.scala         // scripted/recording test handlers
```

### Domain ADTs

```scala
enum LoopExit(val rc: Int):
  case Success      extends LoopExit(0)   // merged or PR -> needs-review
  case ManualStop   extends LoopExit(10)  // STOP.md
  case Idle         extends LoopExit(11)  // no ready/in-progress issue; no sentinel written
  case DryRun       extends LoopExit(20)
  case NothingMade  extends LoopExit(30)  // empty IMPL patch or nothing staged at terminal
  case NeedsHuman   extends LoopExit(40)
  case InfraFault   extends LoopExit(50)  // budget untouched, no PR, label untouched

enum StageResult:
  case Ok(patch: String)               // slice 1: no os-lib dependency yet, see below
  case Empty
  case Timeout                        // infra
  case ApplyFail                      // infra
  case Protected, Oversize            // guard rejection -> needs-human, gate SKIPPED

enum GateResult:   case Green, Red, Timeout
enum Verdict:      case Approve, RequestChanges
enum FailureKind:  case GateRed, ReviewChanges, ProtectedPath, OversizedPatch, EmptyFix
```

### Error channel

Infra faults short-circuit via `Raise[InfraFault]`: any dispatch timeout, apply-fail, empty
review, CI-appear/CI-wait timeout, merge failure or unverified merge raises; the driver folds
it to rc 50. By construction no `Raise` path can reach `budget -= 1` or a FIX dispatch —
"an infra fault never spends budget" becomes a type-level property instead of a convention.

### Capabilities (yaes-style context parameters)

| Capability | Operations | Live handler |
|---|---|---|
| `GitHub` | inProgressIssue, oldestReadyIssue (resume in-progress first, decided in Machine not the handler), issueBody, labels, editLabels, createPr, prComment, prState, checksRollupCount, comment | `gh` subprocess |
| `Git` | statusClean, fetchOriginMain, checkoutBranch, resetHard+clean, applyNumstat, applyIndex, addAll, diffCached, commit, push | `git` subprocess |
| `AgentDispatch` | worker(role, promptFile, patchOut, currentPatch), review(prompt) | wrappers via seam env-var overrides or `sandbox/run-*.sh` under `ITER_TIMEOUT` |
| `GateRunner` | run(cmd, timeout, logFile) | subprocess under `gtimeout` |
| `StatusLog` | phase(phase, state, logfile, detail) | O_APPEND single-write to `status.jsonl` |
| `Notify` | notify(msg) | `NOTIFY_CMD` / ntfy.sh / log-only; failures swallowed |
| `HarnessFs` | prompt templating (`{{KEY}}` splice), log files, markers (PATCH-REJECTED.md, FIX-EMPTY.md), STOP.md check | `os-lib` / java.nio |
| `Clock` | sleeps for CI-appear poll | bespoke one-method trait, not yaes `System`/`Clock` (see below) |

Tests replace every capability with scripted in-memory handlers plus a recorder, so scenarios
assert on both the outcome (exit code, labels flipped, PR opened, budget left) and the
interaction sequence (no FIX after infra fault, no merge without verification, marker staged
on guard rejection).

### Slice-1 deviations

Three places where the built code took a simpler path than this doc sketched. All three are
open to revisiting in slice 2, not closed decisions.

- **`StageResult.Ok` carries a `String`, not `os.Path`.** Slice 1 never took os-lib as a
  dependency; the patch text is a plain string end to end. A richer path type can arrive
  once a live filesystem handler needs one.
- **`pickIssue` is two capability methods, `inProgressIssue` and `oldestReadyIssue`, not
  one.** This keeps the resume-in-progress-first decision inside `Machine`, where the
  scenario tests exercise it, instead of burying it in a handler's own logic.
- **No `Env` capability, and `Clock` is bespoke rather than yaes's `System`/`Clock`.**
  Config is a hardcoded `Config` case class carrying the same defaults this doc lists;
  `Clock` is a one-method trait (`sleepSeconds`) that in-memory tests script directly.
  Env var parsing and a real `Env` capability, if one is still warranted, arrive with
  `Main` in slice 2.

### Semantics ported exactly (the subtle ones)

- Idle (rc 11) writes **no** sentinel — transient, next tick resumes (STOP.md latch bug,
  PR #17).
- `wait_for_checks`: poll `statusCheckRollup` length until > 0 (bounded by
  `CI_APPEAR_TIMEOUT`) **before** letting the CI watch judge; never read the watch's exit
  code as red while zero checks are registered (PR #28 / issue #26).
- Missing `VERDICT:` sentinel on a **non-empty** review = fail-safe `RequestChanges` (spends
  budget); empty/whitespace review = infra fault (spends nothing).
- Patch-guard fail-open is deliberate and backstopped: unparseable patch yields empty
  numstat, guard passes, `git apply --index` then fails → `ApplyFail` (infra), never a gate
  failure.
- Guard rejection on the *initial* IMPL patch skips the repair loop entirely; gate reported
  `SKIPPED`; rejected patch never applied; marker committed; audit PR opened; needs-human.
- Empty FIX patch = fixer reverted all prior work → `FIX-EMPTY.md` marker, needs-human.
- `detail` sanitization in status events (strip `\`, `"`, newlines); logfile paths
  repo-relative; one `printf`-equivalent write per event, under PIPE_BUF.
- Auto-merge (class-1 + APPROVE only): wait-appear → watch → merge → **verify state MERGED**
  (unverified = infra) → drop in-progress → `flip_blocked` (Blocked-by: #N scan, just-merged
  issue counts closed) → fetch → notify.
- Notify fires on exactly: needs-human terminals, rc-50 exits, successful auto-merges.
- Commit message / PR body formats preserved verbatim (parity oracle greps them).

### Entry point

`harness/loop.sh` becomes (slice 4) a thin shim: `exec scala-cli run harness/scala -- "$@"`
with the JDK-25 pin. Until then the Scala loop runs side by side as
`scala-cli run harness/scala`. scala-cli caches compilation, so per-tick JVM startup is the
only overhead (~1 s, irrelevant against a 30-minute iteration).

## Testing

- **ScenarioSpec (scalatest, matching the repo)**: scenarios A–T ported as in-memory tests —
  APPROVE (class-1 auto-merge; class-2 stop-at-PR), REQUEST_CHANGES, fast-RED, budget
  exhaustion by each spender, dispatch timeouts (IMPL/FIX/REVIEW), idle-no-latch, STOP.md,
  DRY_RUN, empty review, missing sentinel, gate timeout, CI red / CI-appear timeout /
  CI-wait timeout / unverified merge / merge failure, patch guard (protected, oversized,
  apply-conflict), empty IMPL, empty FIX, blocked→ready flips.
- **Parity oracle**: `statemachine-test.sh` run against the Scala entry (slice 3 gate).
  Deleted only after the first real US merges under the Scala loop (slice 4 follow-up).
- `sandbox/test/*` unchanged — the wrappers are out of scope.

## Slices

1. **Core.** Scaffold, Domain, Caps, Machine, ScenarioSpec green over in-memory handlers.
   No real side effects. Done: `scala-cli test harness/scala` green with the full scenario
   matrix.
2. **Live handlers.** Subprocess capability implementations, seam env-var overrides, prompt
   templating, status.jsonl writer, notify, preflight (image build, proxy, credential check,
   trap-equivalent proxy stop). Done: `DRY_RUN=1 scala-cli run harness/scala` renders the
   worker prompt against real `gh` and stops, rc 20.
3. **Full loop + parity.** Auto-merge path, blocked→ready, terminal commit/push/PR. Done:
   `statemachine-test.sh` pointed at the Scala entry — 142/142.
4. **Swap.** `loop.sh` → shim, README/docs update, DRY_RUN probe, one real class-1 US
   end-to-end, then delete the bash machine body; bash suite retired after that US merges.

## Risks

- **yaes has no process/file effects.** We write thin wrappers (that is part of the point —
  article material), but it is real work and the first place yaes API gaps will show.
- **Oracle fidelity.** The bash suite asserts on log strings as well as behavior; where a
  message is load-bearing for an assertion, the Scala loop must emit the same line. Slice 3
  budgets for a small compatibility table of such strings.
- **scala-cli on the host.** New host dependency (not in the sandbox image; the Scala loop,
  like `loop.sh`, runs on the host and never inside the sandbox).
