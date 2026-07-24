# Project conventions

## Layout

Scala sources live under `src/main/scala/in/rcard/fes/`. Each aggregate gets its own package
(`patron/`, `copy/`), and inside it the same layer packages:

- `domain/` — the aggregate, its commands, its events, its decider, and its own error type. Where
  the domain needs something from outside, it declares the interface in `domain/port/` (see
  `copy/domain/port/FindCopyByIsbnPort.scala`).
- `application/` — the use cases, the command handlers, and one error enum per use case.
- `adapter/` — HTTP routes.
- `infrastructure/` — event store implementations and everything that talks to Postgres.

`eventsourcing/` holds the aggregate-agnostic machinery: `Decider`, `CommandHandler`,
`EventStorePort`, `PostgresJdbcEventStore`. It knows about no aggregate and must not learn about
one.

The layering rule: dependencies point inward. `domain` imports nothing from the other three.
`adapter` and `infrastructure` never import each other.

### Use-case error types

Each use case defines its own error enum next to it in `application/`, named after the use case
(`RegisterPatronError`, `RegisterCopyError`, `SuspendPatronError`). That enum is the only error
type allowed to cross the seam out of the use case.

Domain errors, such as `Copy.Error` raised by `CopyDecider`, are internal to the domain and get
translated by the use case. Routes never import a domain error.

A use-case error enum must cover every domain error the use case can hit, every port error it can
hit (keeping signals distinct where they map to different HTTP statuses), and a catch-all
`UnexpectedError(message: String)` for infrastructure failures.

Worked example: `RegisterCopyError` has `CopyNotFoundInCatalog(isbn)` for 400,
`AlreadyRegistered(id)` for 409, and `UnexpectedError` for 500. Both the domain's
`Copy.Error.AlreadyRegistered` and the port's `FindCopyByIsbnPort.Error.NotFound` are translated
into it, so the adapter layer sees only `RegisterCopyError`.

## The template to copy

The US-1 Register Patron slice. Read it end to end before writing anything, and copy its shape,
naming and test structure:

- `src/main/scala/in/rcard/fes/patron/domain/PatronDecider.scala`, and `Command.scala`,
  `Event.scala`, `Error.scala` beside it
- `src/main/scala/in/rcard/fes/patron/application/RegisterPatronUseCase.scala` and
  `RegisterPatronError.scala`
- `src/main/scala/in/rcard/fes/patron/adapter/RegisterPatronRoute.scala`
- `src/test/scala/in/rcard/fes/patron/application/RegisterPatronUseCaseSpec.scala`,
  `.../adapter/RegisterPatronRouteSpec.scala`, `.../domain/PatronDeciderSpec.scala`
- `src/test/scala/in/rcard/fes/patron/Fixtures.scala` for the in-memory stubs to reuse rather than
  reinvent

Copying that slice beats any amount of prose here. If something below contradicts what the
Register slice actually does, the slice wins and the contradiction is a bug in this file.

## Test tiers

`src/test/scala` is the fast tier. `sbt test` runs it, it is what gates every iteration, and it
must never need Docker, a database, the network, or a credential. Use the in-memory fixtures and
stubs that already exist.

`src/it/scala` is the slow tier, run by CI as `sbt It/test` against a real Postgres. `It` is a
custom sbt config (`config("it") extend Test`, build.sbt:41) because sbt's built-in
`IntegrationTest` was deprecated in 1.9. It carries the full Test classpath, so Testcontainers and
scalatest are available with no extra wiring.

A Testcontainers or JDBC test placed in `src/test` will not run in the fast gate. It looks green
here and fails in CI, which is the exact failure this split exists to prevent.

Most user stories need no `src/it` test at all. Add one only when an acceptance criterion genuinely
requires a real Postgres round trip.

## Build and lint rules

The project compiles with `-release:25` and `-Werror` (build.sbt:50-51). Every warning is a build
failure. Keep the compile clean rather than working around it.

`.scalafmt.conf` is committed. Formatting is expected to match it.

## Anything that has bitten you

Never reach for `@nowarn` to get past a warning, and never weaken or delete an assertion to reach
green. Both are the same move: the build goes green and the signal that something is wrong is gone.
Fix the cause. A warning under `-Werror` is the build telling you something real.

`CONTEXT.md` is the domain glossary and is worth reading for language. Note one stale line in it:
its "Architectural Conventions" section says use-case error enums live in a `domain.usecase`
package. They do not. Every one of them lives in `application/`. Follow the code.
