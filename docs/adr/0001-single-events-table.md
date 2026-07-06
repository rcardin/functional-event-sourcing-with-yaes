# ADR 0001: Single global events table

## Status
Accepted

## Context
The event store needs a PostgreSQL schema. Two options:
- **Per-aggregate tables** (`copy_events`, `patron_events`, …) — one table per aggregate type.
- **Single global table** (`events`) — all aggregates in one table, discriminated by `aggregate_type`.

`EventStorePort[Id, Event]` is a generic abstraction. A single concrete `PostgresEventStore[Id, Event]` implementation needs to work for any aggregate type without schema changes.

## Decision
Use a single `events` table with columns `(aggregate_id, aggregate_type, sequence_no, event_type, payload, occurred_at)` and a composite primary key on `(aggregate_id, aggregate_type, sequence_no)`.

Each aggregate type gets its own typed instance of `PostgresEventStore`, passing its `aggregateType` string (e.g. `"copy"`) as a constructor parameter.

## Consequences
- Adding a new aggregate type requires no schema migration — only a new `PostgresEventStore` instance.
- The generic `EventStorePort[Id, Event]` port is implementable with a single class.
- The composite primary key enforces optimistic locking for free: inserting a duplicate `(aggregate_id, aggregate_type, sequence_no)` raises a unique constraint violation, mapped to `EventStorePort.Error.VersionConflict`.
- Cross-aggregate queries (e.g. full event log) are possible on one table but are not a current requirement.
