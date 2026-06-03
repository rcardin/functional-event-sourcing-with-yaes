# Domain Context

## Library Domain

The domain models a simplified book library system where patrons borrow and return physical copies of books.

### Actors

- **Librarian** — manages the catalog (copies) and patron accounts.
- **Patron** — borrows and returns copies.

### Aggregates

- **Copy** — a single physical copy of a book. Lifecycle: `Available`, `Borrowed`, `Lost`, `Damaged`, `Removed`.
- **Patron** — a library member. Lifecycle: `Active`, `Suspended`, `Deactivated`. Tracks active loan count against a borrow limit.

### Processes

- A **process manager** coordinates the borrow flow across both aggregates (Copy + Patron).
- Returns are handled by **event-driven choreography**: a reactor on `BookReturned` emits a command to the Patron.

### Key Concepts

- **Event Store** — the append-only log of domain events, keyed by aggregate identity. Supports `load` (replay all events for an aggregate) and `save` (append new events with optimistic concurrency via a version number). Defined by `EventStorePort[Id, Event]`.
- **Version** — a monotonically increasing sequence number per aggregate stream. Used for optimistic locking: a `save` fails with `VersionConflict` if another writer has already appended at the same position.

---

## Architectural Conventions

### Use-case error types

Each use case defines its own error enum in the `domain.usecase` package, named after the use case (e.g. `RegisterCopyError`). This enum is the only error type that crosses the seam into the application layer.

Domain-layer errors (e.g. `Copy.Error` raised by `CopyDecider`) are **internal** to the domain and translated by the use case. The application layer (routes) never imports domain errors directly.

**Cases a use-case error enum must cover:**
- All domain errors the use case can encounter (translated from the aggregate's error type)
- All port errors the use case calls (translated from each port's error type, preserving distinct signals where they affect HTTP semantics)
- A catch-all `UnexpectedError(message: String)` for infrastructure failures

**Example:** `RegisterCopyError` has `CopyNotFoundInCatalog(isbn)` (→ 400), `AlreadyRegistered(id)` (→ 409), `UnexpectedError` (→ 500). The domain's `Copy.Error.AlreadyRegistered` and the port's `FindCopyByIsbnPort.Error.NotFound` are both translated here — the application layer sees only `RegisterCopyError`.
