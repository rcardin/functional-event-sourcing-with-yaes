# HTTP Layer Design — Scala 3 Framework

A record of design decisions and the resulting DSL for the HTTP layer of a new opinionated Scala 3 framework.

---

## Design Decisions

### 1. Concurrency model

**Decision:** Direct-style via `boundary`/`break` (Ox/gears pattern).

No `F[_]` effect type. No monad transformer stacks. Handlers are plain Scala 3 functions. Loss of cats-effect ecosystem compatibility is accepted in exchange for readability and simplicity.

---

### 2. Endpoint input contract

**Decision:** A case class where each field is wrapped in an opaque location type.

```scala
case class GetUser(id: Path[Int], lang: Query[Option[String]])
case class CreateUser(tenant: Header[String], payload: Body[UserPayload])
```

Location wrappers: `Path[A]`, `Query[A]`, `Header[A]`, `Body[A]`.

All four are **opaque types** (zero runtime allocation). They are distinct at compile time, identical to `A` at runtime. The framework's derivation machinery uses `Mirror` to inspect field types at compile time and generate the correct extraction logic.

Only one `Body[A]` field per case class is allowed — enforced by the macro at compile time.

---

### 3. Optional parameters

`Query[Option[A]]` expresses an optional query parameter. `FromParam[Option[A]]` is derived trivially: absent param becomes `Right(None)`, present param delegates to `FromParam[A]`.

---

### 4. Param parsing typeclass

**Decision:** A framework-owned `FromParam[A]` typeclass for all scalar conversions (path segments, query params, headers).

```scala
trait FromParam[A]:
  def parse(s: String): Either[String, A]
```

`Reads[A]`/`Writes[A]` are kept JSON-specific and are not reused for param parsing.

---

### 5. JSON serialization

**Decision:** Framework-owned `Reads[A]` and `Writes[A]` typeclasses. Zero JSON library dependency in core.

```scala
trait Reads[A]:  def decode(s: String): Either[String, A]
trait Writes[A]: def encode(a: A): String
```

A separate `framework-circe` module bridges to circe:

```scala
given [A: io.circe.Encoder]: Writes[A] = a => io.circe.Encoder[A].apply(a).noSpaces
given [A: io.circe.Decoder]: Reads[A]  = s => io.circe.parser.decode[A](s).left.map(_.message)
```

Users derive circe instances as usual and get `Reads`/`Writes` for free via the bridge. No name collision with circe's `Encoder`/`Decoder`.

---

### 6. Validation

**Decision:** Newtype and refined types. Validation happens at parse time inside `FromParam[A]` or `Reads[A]` instances. Handlers receive already-valid values. Invalid states are unrepresentable.

```scala
opaque type NonEmptyString = String
object NonEmptyString:
  def apply(s: String): Either[String, NonEmptyString] =
    if s.nonEmpty then Right(s) else Left("must not be empty")

given FromParam[NonEmptyString] = s => NonEmptyString(s)
```

---

### 7. Error accumulation

**Decision:** Always accumulate all field-level parse errors. Never fail fast.

All errors are returned as **RFC 9457 Problem Details** (`application/problem+json`), including both framework parsing errors and handler-returned errors.

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Request parameters failed validation",
  "errors": [
    { "field": "id",   "message": "not an int" },
    { "field": "lang", "message": "required" }
  ]
}
```

---

### 8. Response model

**Decision:** Fixed sealed `Response[+A]` hierarchy. All error constructors emit RFC 9457. A `Raw` escape hatch allows full control when needed.

```scala
sealed trait Response[+A]
case class Ok[A](body: A)              extends Response[A]
case class Created[A](body: A)         extends Response[A]
case class NotFound(detail: String)    extends Response[Nothing]
case class BadRequest(detail: String)  extends Response[Nothing]
case class Unauthorized(detail: String) extends Response[Nothing]
case class Forbidden(detail: String)   extends Response[Nothing]
case class Raw(status: Int, body: String) extends Response[Nothing]
```

---

### 9. Endpoint type

**Decision:** `Endpoint[I, O]` where `I` is the input case class and `O` is the **success body type**. Error types are not encoded in the type signature (door left open for a future third type param).

Handler return type is `Response[O]`.

---

### 10. Endpoint description vs handler

**Decision:** Endpoint description is a pure value, separate from the handler. This preserves the ability to generate OpenAPI docs, reuse endpoints as client descriptors, and unit-test shapes without running the server.

---

### 11. `RouteInput[I]` typeclass

**Decision:** `RouteInput[I]` is a framework typeclass derived automatically via `Mirror`. Users never write `derives RouteInput`. The `GET[I](...)` call site triggers transparent implicit derivation.

A manually written `given RouteInput[I]` serves as an escape hatch.

---

### 12. HTTP method DSL

**Decision:** Type parameter style. The input case class is provided as a type argument.

```scala
GET[GetUser]("users/{id}").out[User]
```

A macro validates at compile time that every `Path[_]` field in `GetUser` has a matching `{fieldName}` segment in the path string.

---

### 13. Routing composition

**Decision:** `List[ServerEndpoint]` is the semantic core. `Router("/prefix")` is sugar that prepends a path segment to each endpoint in the list.

---

### 14. Middleware

**Decision:** Middleware is a plain function `List[ServerEndpoint] => List[ServerEndpoint]`. No framework `Middleware` trait. Composes with `andThen`.

---

### 15. Backend

**Decision:** Pluggable via an `HttpBackend` typeclass. Implementation deferred. Netty is the intended default.

---

## Full DSL Sketch

```scala
import framework.core.*
import framework.circe.*  // bridges circe Encoder/Decoder → Writes/Reads

// --- Domain types ---

case class UserPayload(name: NonEmptyString, age: PosInt)
case class User(id: Int, name: String, age: Int)

// --- Input contracts ---

case class GetUser(
  id:   Path[Int],
  lang: Query[Option[String]]
)

case class CreateUser(
  tenant:  Header[String],
  payload: Body[UserPayload]
)

// --- Endpoints (pure descriptions) ---

val getUser: Endpoint[GetUser, User] =
  GET[GetUser]("users/{id}").out[User]

val createUser: Endpoint[CreateUser, User] =
  POST[CreateUser]("users").out[User]

// --- Server endpoints (description + handler) ---

val getUserServer: ServerEndpoint =
  getUser.handle { req =>
    findUser(req.id.value, req.lang.value) match
      case Some(u) => Ok(u)
      case None    => NotFound(s"user ${req.id.value} not found")
  }

val createUserServer: ServerEndpoint =
  createUser.handle { req =>
    Ok(persistUser(req.payload.value))
  }

// --- Middleware ---

val withLogging: List[ServerEndpoint] => List[ServerEndpoint] =
  eps => eps.map(_.withLogging)

val withAuth: List[ServerEndpoint] => List[ServerEndpoint] =
  eps => eps.map(ep => ep.contramap(req => authenticate(req)))

// --- Server startup ---

val routes: List[ServerEndpoint] =
  List(getUserServer, createUserServer)

Server.start(
  port   = 8080,
  routes = withLogging(withAuth(routes))
)
```

---

## Typeclass Summary

| Typeclass | Purpose | Lives in |
|---|---|---|
| `RouteInput[I]` | Derives field extraction from case class | `framework-core` |
| `FromParam[A]` | Parses path/query/header string → `A` | `framework-core` |
| `Reads[A]` | Decodes JSON body string → `A` | `framework-core` |
| `Writes[A]` | Encodes `A` → JSON string | `framework-core` |

---

## Module Structure

```
framework-core      — Endpoint, ServerEndpoint, Response, typeclasses, macros
framework-circe     — Reads/Writes bridges for circe
framework-netty     — Default HttpBackend implementation (Netty)
```
