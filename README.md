# Functional Event-Sourcing with YAES
An example of how to use the YAES effect system to model a system using functional event-sourcing.

## Domain
The domain is a simplified book library system. The system allows users to borrow and return books. The system also keeps track of the inventory of books.

### Actors
- **Librarian** — manages the catalog (copies) and patron accounts.
- **Patron** — borrows and returns copies.

### Aggregates
- **Copy** — a single physical copy of a book. Rich lifecycle: `Available`, `Borrowed`, `Lost`, `Damaged`, `Removed`.
- **Patron** — a library member. Lifecycle: `Active`, `Suspended`, `Deactivated`. Tracks active loan count against a borrow limit.

A **process manager** coordinates the borrow flow across both aggregates. Returns are handled by **event-driven choreography** (a reactor on `BookReturned` emits a command to the Patron).

## User Stories

### 1. Catalog management (Librarian)
- **US-1** — *Register a copy.* As a librarian, I want to register a new physical copy of a book (with its title/ISBN metadata) so that it becomes available for patrons to borrow.
- **US-2** — *Mark a copy as lost.* As a librarian, I want to mark a copy as lost so that it can no longer be borrowed and the catalog reflects reality.
- **US-3** — *Mark a copy as damaged.* As a librarian, I want to mark a copy as damaged so that it's withdrawn from circulation pending repair.
- **US-4** — *Repair a damaged copy.* As a librarian, I want to return a damaged copy to circulation once repaired so that it can be borrowed again.
- **US-5** — *Remove a copy.* As a librarian, I want to permanently remove a copy from the catalog so that retired or destroyed copies are no longer tracked as active inventory.

**Invariants:** a copy can only be borrowed if `Available`; you can't repair a copy that isn't `Damaged`; you can't remove a copy that's currently `Borrowed`.

### 2. Patron management (Librarian)
- **US-6** — *Register a patron.* As a librarian, I want to register a new patron with a borrow limit so that they can start borrowing books up to that limit.
- **US-7** — *Suspend a patron.* As a librarian, I want to suspend a patron so that they cannot borrow new books until reinstated.
- **US-8** — *Reinstate a patron.* As a librarian, I want to reinstate a previously suspended patron so that they can borrow books again.
- **US-9** — *Deactivate a patron.* As a librarian, I want to permanently deactivate a patron so that the account is closed and no further actions are possible on it.

**Invariants:** a suspended patron cannot borrow (but can still return); deactivation is terminal; a patron with active loans cannot be deactivated; reinstate is only valid from `Suspended`.

### 3. Borrowing (Patron, coordinated by a process manager)
- **US-10** — *Borrow a copy.* As a patron, I want to borrow an available copy of a book so that I can read it at home.
- **US-11** — *Be prevented from borrowing when over the limit.* As a patron, I want the system to reject my borrow request if I already hold my maximum number of loans.
- **US-12** — *Be prevented from borrowing when suspended.* As a patron, I want the system to reject my borrow request if my account is suspended.
- **US-13** — *Be prevented from borrowing an unavailable copy.* As a patron, I want the system to reject my borrow request if the copy is already borrowed, lost, damaged, or removed.
- **US-14** — *Consistent outcome under concurrent attempts.* As a librarian/patron, I want two patrons racing to borrow the same copy to result in exactly one success so that the copy is never double-loaned.

**Invariants:** borrow succeeds only if both Patron (active, under limit) and Copy (available) accept; if the Copy cannot be reserved after the Patron-side reservation, the Patron side is compensated so the loan count is not incremented; the outcome is atomic from the client's perspective — either both `BookBorrowed` (Copy) and `LoanOpened` (Patron) are persisted, or neither.

### 4. Returning (Patron, event-driven choreography)
- **US-15** — *Return a borrowed copy.* As a patron, I want to return a copy I borrowed so that it becomes available for others and my active-loan count decreases.
- **US-16** — *Return a damaged copy.* As a patron, I want to return a copy and flag it as damaged so that the librarian knows it needs repair before circulating again.
- **US-17** — *Loan count updates automatically on return.* As a patron, I want my active-loan count to decrement automatically when I return a copy so that I can borrow again up to my limit without librarian intervention.
- **US-18** — *Return is accepted even when suspended.* As a patron, I want to be able to return copies even while suspended so that suspension never traps loans in my account.

**Invariants:** return is issued to the Copy aggregate, transitioning `Borrowed → Available` (or `Borrowed → Damaged` for US-16); a reactor observes `BookReturned` and emits a command to the owning Patron, producing `LoanReleased`; the two state changes are eventually consistent — the patron's counter may briefly overstate active loans, which is acceptable because returns cannot violate any invariant; returning a copy that isn't `Borrowed` is rejected.
