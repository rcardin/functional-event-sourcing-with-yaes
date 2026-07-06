# ADR 0003: Derive current status by folding the event stream

## Status
Accepted

## Context
`CopyState` is `Seq[Event]`, and the `Domain` predicates (`isRegistered`, `isLost`, `isDamaged`) were written as existence checks — `copyState.exists { case MarkedAsDamaged(id) => … }` — i.e. "did this event *ever* occur." Every lifecycle transition so far was monotonic (once `Lost`, always `Lost`), so existence and current-state coincided and the flaw was invisible.

US-4 (*Repair a damaged copy*) introduces the first **reversible** transition, `Damaged → Available`. A `MarkedAsDamaged` event stays in the append-only log forever, so an existence-based `isDamaged` would report a repaired copy as still damaged, and the invariant "you can't repair a copy that isn't Damaged" cannot be expressed against "ever damaged."

## Decision
Keep `CopyState` as `Seq[Event]` (no materialised case class). Introduce a `Status` enum and a `currentStatus(id): Status` fold that reduces the stream to the **last** lifecycle event for that id. Rewrite `isRegistered`/`isLost`/`isDamaged` in terms of `currentStatus`. `Repaired` maps back to `Available`; there is no `Repaired` status.

## Considered Options
- **Add another existence predicate** (`isRepaired`) and special-case `isDamaged` to exclude ids that were later repaired. Rejected: it spreads the "ever vs now" trap across every future reversible transition.
- **Materialise `CopyState` into a case class with a `status` field.** Rejected for now: larger blast radius across every existing predicate and decider path for a single-story need; the fold is the smaller, reversible step and can be promoted later if the read model grows.

## Consequences
- The `Status` enum is kept **minimal** — `NotRegistered`, `Available`, `Lost`, `Damaged`. `Borrowed`/`Removed` are added when US-5/US-10 introduce events that produce them; unreachable cases are not pre-declared.
- Existing US-1/US-2/US-3 predicates are rewritten on top of the fold and must stay green.
- Future reversible transitions (return, reinstate-style flows) reuse `currentStatus` instead of adding existence checks.
