# DB Layer Design — Scala 3 Framework

A record of design decisions and the resulting DSL for the DB layer of a new opinionated Scala 3 framework.

---

## Design Decisions

### 1. Target database

**Decision:** SQL only. PostgreSQL is the primary target. A `framework-db-mysql` module can appear later with no changes to core.

---

### 2. Query model

**Decision:** Typed AST DSL for single-table queries + raw SQL escape hatch for everything else.

CRUD ops are derived automatically. Complex queries use a macro-based `sql"..."` interpolator with typed parameters. The DSL builds an internal typed expression tree; SQL is generated from that tree. Field references are compile-time validated via `Mirror`. SQL string content is not validated at compile time — that tradeoff is accepted in exchange for implementation tractability.

Compile-time safety boundary: **Scala boundary** (field names, types, operators). Runtime safety boundary: **SQL boundary** (query semantics).

Joins are out of scope for v1. The AST tracks table source from day one so join support can be added later without breaking changes.

---

### 3. Entity definition — opaque location types

**Decision:** Any case class with at least one `PK[_]` field is automatically an entity. No `derives`, no annotations.

```scala
case class UserEntity(
  id:        PK[AutoInc[Int]],
  firstName: Col["fname", String],
  age:       Int
)
```

All field-level DB metadata is expressed via **opaque types** (zero runtime allocation), identical to the wrapped type at runtime. The framework's derivation machinery uses `Mirror` to inspect field types at compile time.

| Opaque type | Meaning |
|---|---|
| `PK[A]` | This field is (part of) the primary key |
| `PK[AutoInc[A]]` | PK is database-generated — omitted from `INSERT`, returned after |
| `Col[Name <: String, A]` | Override the column name convention; `Name` is a string literal type |

`Col["fname", String]` is valid Scala 3: string literals are types, and `constValue[Name]` extracts the name at compile time with zero runtime cost.

Multiple `PK[_]` fields = composite primary key. The framework generates `WHERE pk1 = ? AND pk2 = ?` automatically.

`Col[N, A]` is used only when the default snake_case convention does not match the actual column name. When convention fits, plain `String`, `Int`, etc. are used directly.

Only `PK[A]` and `Col[N, A]` are defined in v1. `FK[A]` and `Unique[A]` are deferred.

---

### 4. Case class to table mapping

**Decision:** Convention over configuration. Column name overrides are inline via `Col[N, A]`. Table name overrides are external via a narrow `given TableName[A]`.

- Class name → snake_case plural: `UserEntity` → `user_entities`
- Field name → snake_case: `firstName` → `first_name`
- Field typed as `Col["fname", String]` → column name `fname`

Override the table name when convention does not fit:

```scala
given TableName[UserEntity] = "users"
```

Column overrides live in the case class itself (`Col[N, A]`), consistent with how `PK[A]` signals primary key structure inline. Table name is the only concern that cannot be expressed inside the case class and therefore uses a minimal external `given`.

---

### 5. Automatic derivation chain

From the entity definition the framework derives automatically:

| Derived | Source | Purpose |
|---|---|---|
| `Table[A]` | `Mirror` + optional `given TableName[A]` | Table name, column mapping, PK fields |
| `RowDecoder[A]` | `Mirror` | Maps `ResultSet` row → `A` |
| `RowEncoder[A]` | `Mirror` | Maps `A` → `PreparedStatement` parameters |
| `given Repository[A]` | `Table[A]` + `DbSession` | All CRUD operations |

Column mapping and PK detection come entirely from the case class (`Col[N,A]`, `PK[A]`). Table name comes from convention (class name → snake_case plural) and is the only concern that requires an external `given TableName[A]` override.

---

### 6. `Repository[A]` and CRUD

**Decision:** `Repository[A]` is a `given` auto-derived from `Table[A]` + `DbSession`. It is a capability injected via `using`, not constructed explicitly.

```scala
def registerUser(payload: UserPayload)(using repo: Repository[UserEntity]): Either[DbError, UserEntity] =
  repo.insert(
    UserEntity(id = PK(AutoInc), firstName = payload.name, age = payload.age)
  )
```

Available operations:

```scala
repo.findById(id)          // Either[DbError, Option[A]]
repo.query(q: Query[B])    // Either[DbError, List[B]]  — B inferred from query, RowDecoder[B] derived
repo.insert(a: A)          // Either[DbError, A]  (returns with generated id)
repo.update(a: A)          // Either[DbError, A]  (by PK)
repo.delete(id)            // Either[DbError, Unit] (by PK)
```

Bulk `update` and bulk `delete` are intentionally absent from the DSL. Use the `sql"..."` escape hatch for those — making dangerous ops harder to write by accident is a feature.

---

### 7. `Query[A]` — typed AST DSL

**Decision:** `Query[A]` is a pure immutable value describing a query. It has no `.run` method. Execution always goes through `Repository[A]`.

```scala
val q: Query[(String, Int)] =
  Query[UserEntity]
    .filter(_.age > 18)
    .select(u => (u.firstName, u.age))   // field refs only — compile-time validated
    .orderBy(_.firstName, Asc)
    .limit(10)
    .offset(20)

val result: Either[DbError, List[(String, Int)]] =
  summon[Repository[UserEntity]].query(q)
```

`select` changes the row type: `Query[UserEntity]` becomes `Query[(String, Int)]`. The macro inspects the lambda at compile time, extracts field refs, generates `SELECT first_name, age` and derives a `RowDecoder[(String, Int)]` from column positions. Arbitrary expressions in `select` (e.g. `.toUpperCase`) are rejected at compile time — use the `sql"..."` escape hatch.

`repo.query` accepts any `Query[B]` — the result type `B` is inferred from the query, not from the repository's entity type `A`. `RowDecoder[B]` is derived automatically by the macro for tuples and primitive types.

#### Filter operators

```scala
_.age === 18
_.age =!= 18
_.age > 18
_.age >= 18
_.age < 18
_.age <= 18
_.name.like("Al%")
_.name.in(List("Alice", "Bob"))
_.email.isNull
_.email.isNotNull
p1 && p2
p1 || p2
!p1
```

---

### 8. Error model

**Decision:** `Either[DbError, A]` for all repository and query operations.

```scala
sealed trait DbError
case class UniqueViolation(detail: String) extends DbError
case class UnexpectedError(cause: String)  extends DbError
```

---

### 9. Raw SQL escape hatch

**Decision:** `sql"..."` macro-based string interpolator. Parameters are typed — no SQL injection. Column and table names can be injected as compile-time-safe references.

```scala
val age = 18
val result: Either[DbError, List[UserEntity]] =
  sql"SELECT * FROM ${Table[UserEntity]} WHERE ${col[UserEntity](_.age)} > $age"
    .as[UserEntity]
    .run
```

`$age` → `?` in `PreparedStatement`, typed. `Table[UserEntity]` and `col[UserEntity](_.age)` are resolved at compile time via `Mirror`. `.as[A]` uses the auto-derived `RowDecoder[A]`.

---

### 10. `DbSession` and transactions

**Decision:** `DbSession` is a singleton capability wrapping the connection pool. It borrows a connection per operation and returns it immediately. A `transaction { }` block borrows one connection for its entire scope.

```scala
given DbSession = DbSession.fromConfig()  // reads standard config keys

val result: Either[DbError, UserEntity] =
  session.transaction {
    val user = summon[Repository[UserEntity]].insert(UserEntity(...))
    summon[Repository[AddressEntity]].insert(AddressEntity(userId = user.id.value, ...))
    user
  }
```

Transaction semantics:
- Returns `Either[DbError, A]`
- Any `Left` or uncaught exception rolls back and returns `Left`
- Success commits and returns `Right(a)`
- Nested `transaction { }` calls join the outer transaction (flat — no savepoints in v1)

---

### 11. Startup wiring

**Decision:** `DbSession.fromConfig()` reads standard config keys (url, user, password, pool size) and constructs the pool. `DbSession(pool)` is available for custom pool configuration.

```scala
@main def run(): Unit =
  given DbSession = DbSession.fromConfig()
  // given Repository[UserEntity] now auto-derived anywhere in scope
  Server.start(port = 8080, routes = myRoutes)
```

---

### 12. Migrations

**Decision:** Delegate to Flyway. SQL migration files live in `db/migration/V<n>__<description>.sql`. The `framework-db-flyway` module runs `migrate()` at startup automatically when `given DbSession` is in scope.

Framework-generated DDL from `Table[A]` (derive `CREATE TABLE` from entity) is deferred to v2.

---

## Full DSL Sketch

```scala
import framework.db.core.*
import framework.db.postgres.*
import framework.db.flyway.*

// --- Entity definitions ---

case class UserEntity(
  id:        PK[AutoInc[Int]],
  firstName: String,
  age:       Int
)

case class AddressEntity(
  id:     PK[AutoInc[Int]],
  userId: Int,
  street: String
)

// No additional code needed. Table, RowDecoder, RowEncoder,
// Repository[UserEntity], Repository[AddressEntity] all derived automatically.

// --- Override convention when needed ---

// Column name override: inline via Col[N, A]
case class UserEntity(
  id:        PK[AutoInc[Int]],
  firstName: Col["fname", String],  // maps to column "fname" instead of "first_name"
  age:       Int
)

// Table name override: external, narrow given
given TableName[UserEntity] = "users"

// --- App startup ---

@main def run(): Unit =
  given DbSession = DbSession.fromConfig()
  Server.start(port = 8080, routes = myRoutes)

// --- Simple query ---

def findAdults()(using repo: Repository[UserEntity]): Either[DbError, List[UserEntity]] =
  repo.query(
    Query[UserEntity].filter(_.age >= 18).orderBy(_.firstName, Asc)
  )

// --- Projection ---

def adultNames()(using repo: Repository[UserEntity]): Either[DbError, List[String]] =
  repo.query(Query[UserEntity].filter(_.age >= 18).select(_.firstName))

// --- Transaction ---

def registerUser(payload: UserPayload)(using session: DbSession): Either[DbError, UserEntity] =
  session.transaction {
    val user = summon[Repository[UserEntity]].insert(
      UserEntity(id = PK(AutoInc), firstName = payload.name, age = payload.age)
    )
    summon[Repository[AddressEntity]].insert(
      AddressEntity(id = PK(AutoInc), userId = user.id.value, street = payload.street)
    )
    user
  }

// --- Raw SQL escape hatch ---

def usersInCity(city: String)(using session: DbSession): Either[DbError, List[UserEntity]] =
  sql"""
    SELECT u.*
    FROM ${Table[UserEntity]} u
    JOIN addresses a ON a.user_id = ${col[UserEntity](_.id)}
    WHERE a.city = $city
  """.as[UserEntity].run
```

---

## Typeclass Summary

| Typeclass | Purpose | Lives in |
|---|---|---|
| `Table[A]` | Table name, column mapping, PK fields | `framework-db-core` |
| `TableName[A]` | Table name override (escape hatch) | `framework-db-core` |
| `RowDecoder[A]` | Maps `ResultSet` row → `A` | `framework-db-core` |
| `RowEncoder[A]` | Maps `A` → `PreparedStatement` params | `framework-db-core` |
| `Repository[A]` | CRUD operations | `framework-db-core` |

---

## Module Structure

```
framework-db-core      — Query[A], Table[A], PK[A], Col[N,A], BoolExpr AST,
                         RowDecoder/RowEncoder, DbSession, DbError,
                         sql"..." interpolator, Repository[A]
framework-db-postgres  — PostgreSQL JDBC backend, HikariCP pool wiring
framework-db-flyway    — Flyway integration, migration runner at startup
```
