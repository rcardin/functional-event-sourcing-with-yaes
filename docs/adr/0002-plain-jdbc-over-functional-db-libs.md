# ADR 0002: Plain JDBC over Doobie/Skunk

## Status
Accepted

## Context
The project uses YAES as its effect system. YAES provides its own `Sync`, `Raise`, `Resource`, and `Log` capabilities — it is not built on `cats.effect.IO`.

The two most idiomatic Scala database libraries are:
- **Doobie** — functional JDBC wrapper requiring `cats.effect.IO` (or any `Async[F]`).
- **Skunk** — purely functional PostgreSQL client, also `cats.effect` based.

Both would introduce `cats.effect` as a dependency, creating a second, conflicting effect system alongside YAES.

## Decision
Use the plain PostgreSQL JDBC driver (`org.postgresql:postgresql`) wrapped directly in YAES `Sync` blocks, with HikariCP for connection pooling and Flyway for schema migrations.

## Consequences
- No `cats.effect` dependency — the YAES effect model remains the single effect system in the project.
- SQL is written as plain strings; no compile-time query checking (Doobie/Skunk trade-off accepted).
- HikariCP is the standard JVM connection pool and integrates trivially with plain JDBC.
- If YAES adds a first-party SQL module in future, migration from plain JDBC is straightforward.
