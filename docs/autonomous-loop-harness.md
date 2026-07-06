# Autonomous Loop Harness — Design

How the remaining user stories (US-2…US-18) get built by an autonomous Ralph loop, with a
human-gated front end and a layered auto-merge gate stack. Product of a grilling session.

## Goal

Implement US-2…US-18 (Copy lifecycle extensions, full Patron aggregate, borrow process
manager, return choreography) via unattended agentic iterations, while keeping auto-merge
risk at the bare minimum.

## Two phases

```
HUMAN-GATED FRONT END (once, up front)        AUTONOMOUS LOOP (unattended)
  grill / brainstorm per epic         ──►       bash state machine over GitHub issues
  writing-implementation-specs                  one narrow claude -p task per iteration
  github-triage -> GitHub issues                layered gate stack -> auto-merge
        (you review the batch)                  needs-human fallback
```

The **spec is the contract** between phases. Once issues exist and are approved, you are
hands-off until `needs-human` fires.

## Core decisions

| Branch | Decision |
|---|---|
| Harness | External orchestration. Ralph bash loop, headless `claude -p`, **fresh context per iteration**. Durable state in git + GitHub issues + files; disposable context in the model. |
| Why not `/loop` | Single accumulating session → auto-compaction → context rot. Loses the smart zone by iter 5-6. Ralph starts cold at full quality every iteration. |
| Task source | GitHub Issues (queue + done-marking + audit). Rich specs in `specs/` in-repo; issue links to its spec file. |
| Granularity | **ATDD**: one acceptance criterion = one sub-task. Planning iter writes all acceptance tests red; each worker iter greens exactly one. US done = 0 red tests. |
| Coherence | A **frozen design note** per US (state model, decider signature, file list, acceptance criteria) keeps cold iterations from contradicting each other. |
| Design note authorship | Class-2/3: human-authored in the spec. Class-1: loop's planning iter writes it. |
| Spec scope | **Tiered.** Class-2/3 get full reviewed specs + frozen design. Class-1 get thin issues (US text + acceptance criteria + deps); loop plans them. |
| Verification gate | Per-iter (worker): compiles under `-Werror`, RED tests allowed. US-boundary: full `sbt test` green. |
| Test strategy | ATDD acceptance + unit tests **in-memory** (`InMemoryEventStore`, stub HTTP) — fast, every iter. Thin per-aggregate **Testcontainers** layer (real PG + Flyway) for JDBC event store round-trip + route e2e — US boundary only. Pre-pull the postgres image once. |
| Loop environment | Loop runs **on host** (Mac/VM); Testcontainers talks to host Docker daemon (mounted socket). No DinD. Image cache persists across iters. |
| Concurrency | **Serial.** One US at a time. Parallel loops race on main and on issue labels. |
| Merge policy | Broad auto-merge made safe by a layered gate stack + independent reviewer + external CI. Human is the exception handler, not a routine reviewer. |
| Loop guards | Exhaustion (no `ready` issues) → write `STOP.md`, exit. Hard iteration cap. Per-iteration `timeout` (kill hung iters). |

## The spine: bash owns the state machine

The LLM never chooses what to work on. **Bash** resolves all state with `gh` queries +
conditionals (deterministic, no LLM) and dispatches **one narrow task** per `claude -p`.

```
each loop tick (bash):
  STOP.md exists or iter-cap hit?          -> exit
  pick US: in-progress one, else next ready (deps satisfied)
  no ready US?                             -> write STOP.md, exit
  has frozen design note + red acceptance tests?
     no  -> dispatch PLAN task   (write design note [class-1] + red acceptance tests)
     yes -> any red acceptance test?
              yes -> dispatch GREEN task (make ONLY this test pass)
              no  -> run US-boundary GATE
```

LLM is invoked for exactly five creative acts: **plan, write-test, write-impl,
fix-on-fail, review.** Everything else (which US, which test, done-detection, label
flips, self-repair counting) is bash.

## Gate stack (before any auto-merge)

Mechanical, scripted, fast:
1. Compile under `-Werror`.
2. Full `sbt test` green — ATDD acceptance + unit (in-memory).
3. Testcontainers integration green — real PG round-trip + route e2e.
4. Every acceptance criterion maps to ≥1 test (count vs design note) — blocks hollow slices.
5. Rebase on latest `main`, re-run suite — proves no regression of earlier USs.
6. Convention lint — CONTEXT.md rules (domain errors don't leak to app layer, use-case
   error enum exists, package layout).
7. **Test-tamper check** — `git diff` test files vs base; surface deletions/weakenings to
   the reviewer. Catches the classic Ralph failure: a cold iter deletes the failing test
   to go green under self-repair pressure.

Independent reviewer (zero shared context with the author):
8. A **separate cold `claude -p` review pass** — adversarial, reviews the diff against the
   issue's acceptance criteria, the frozen design note, CONTEXT.md conventions, and the
   tamper report. Emits `APPROVE` / `REQUEST_CHANGES`.

External verification (the loop cannot fake it):
9. **GitHub Actions** re-runs compile + test + testcontainers on the PR. Branch protection
   makes auto-merge *require* this check green. GH-hosted runners have Docker.

Control flow:
10. All green + APPROVE + CI green → `gh pr merge --squash` (PR kept as audit trail) → next US.
11. Any gate fails or REQUEST_CHANGES → **bounded self-repair** (≤2 fix iterations) → still
    failing → label `needs-human`, skip US, continue others.
12. Git guardrails hook blocks force-push / history rewrite throughout.

## Races need tests, not reviews

An LLM reading a diff cannot prove absence of a race. US-14 (exactly-one-success under
concurrent borrow) needs a **concurrent stress test, human-authored in the spec** — never
"loop translates criteria → test", because a cold iter writes a non-concurrent test that
passes trivially. For class-3, the acceptance test *is* the gate's teeth.

## Labels (unified with github-triage lifecycle)

`ready` · `blocked` · `in-progress` · `planned` · `needs-review` · `needs-human`
plus `class-1` | `class-2` | `class-3`. Bash flips `blocked`→`ready` when deps close.

## Story classes

- **Class 1 — incremental transition on existing aggregate** (US-2,3,4,5,7,8,9): one decider
  case + command + event + use case + route + tests. One cold iteration. Full auto.
- **Class 2 — first slice of a new aggregate** (US-6 register patron): carries Patron
  scaffolding. Full spec + frozen design.
- **Class 3 — cross-aggregate coordination** (US-10…14 borrow PM + race, US-15…18 return
  choreography): hard reasoning. Full spec + human-authored concurrency tests.

## Build sequence (probe-first)

```
v0: bash loop + compile/test gate + stop-at-PR   -> run on US-2 (class-1), watch it break
v1: + independent reviewer agent
v2: + Testcontainers integration layer
v3: + GitHub Actions CI check + branch protection
v4: + auto-merge -> unleash on the backlog
```

First real run is a cheap probe on one easy story, not an overnight bet against imagined
failure modes.

## Open items (decide before unleashing)

- **Observability**: how you see overnight progress + per-iter cost; how `needs-human`
  reaches you (PushNotification?). Loop logs `claude -p --output-format stream-json`.
- **Cost**: confirm iter-cap × worst-case Opus iteration is a dollar figure you can stomach
  (cost ceiling was traded for the hard iteration cap).
- **Permissions**: headless uses `--dangerously-skip-permissions`; runs on host with repo
  write + `gh` + Docker socket. Scope the box accordingly.

## Front-end pipeline skills

1. `superpowers:brainstorming` — intent per epic.
2. `writing-implementation-specs` — per-US spec (acceptance criteria, signatures, deps,
   tests, done-criteria) into `specs/`.
3. `github-triage` — specs → labelled GitHub issues with dependency links. Render the
   dependency graph for approval before launch.
