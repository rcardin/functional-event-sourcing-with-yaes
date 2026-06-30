package in.rcard.fes.eventsourcing

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import in.rcard.fes.eventsourcing.EventStorePort.Valuable
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import io.circe.{Decoder, Encoder}
import org.flywaydb.core.Flyway
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName

/** Integration test for [[PostgresJdbcEventStore]] backed by a real PostgreSQL via Testcontainers.
  *
  * Requires a running Docker daemon. A single container is shared for the whole spec
  * ([[ForAllTestContainer]]); the `events` table is created through the production Flyway migration
  * and truncated before every test for isolation.
  */
class PostgresJdbcEventStoreSpec
    extends AnyFlatSpec
    with TestContainerForAll
    with BeforeAndAfterEach
    with SyncSpec
    with RaiseSpec
    with Matchers {

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:16-alpine"))

  private val aggregateType = "test-aggregate"

  // Test-local types: keep the store under test decoupled from the domain model.
  private final case class TestId(value: String)

  private given Valuable[TestId] with
    def value(id: TestId): String = id.value

  private enum TestEvent:
    case Created(name: String)
    case Updated(value: Int)

  private given Encoder[TestEvent] = Encoder.AsObject.derived
  private given Decoder[TestEvent] = Decoder.derived

  private var pool: HikariDataSource = scala.compiletime.uninitialized

  private def underTest: EventStorePort[TestId, TestEvent] =
    PostgresJdbcEventStore[TestId, TestEvent](pool, aggregateType)

  override def afterContainersStart(container: PostgreSQLContainer): Unit = {
    val config = new HikariConfig()
    config.setJdbcUrl(container.jdbcUrl)
    config.setUsername(container.username)
    config.setPassword(container.password)
    pool = new HikariDataSource(config)
    Flyway.configure().dataSource(pool).load().migrate()
  }

  override def beforeContainersStop(container: PostgreSQLContainer): Unit =
    if pool != null then pool.close()

  override def beforeEach(): Unit = {
    val conn = pool.getConnection()
    try {
      val stmt = conn.createStatement()
      try stmt.execute("TRUNCATE TABLE events")
      finally stmt.close()
    } finally conn.close()
  }

  private def countEvents(): Int = {
    val conn = pool.getConnection()
    try {
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery("SELECT COUNT(*) FROM events")
        rs.next()
        rs.getInt(1)
      } finally stmt.close()
    } finally conn.close()
  }

  // Inserts a row directly, bypassing the store, to set up reads the store API cannot produce.
  private def directInsert(
      aggregateId: String,
      aggType: String,
      sequenceNo: Long,
      eventType: String,
      payload: String
  ): Unit = {
    val conn = pool.getConnection()
    try {
      val stmt = conn.prepareStatement(
        "INSERT INTO events (aggregate_id, aggregate_type, sequence_no, event_type, payload) VALUES (?, ?, ?, ?, ?::jsonb)"
      )
      try {
        stmt.setString(1, aggregateId)
        stmt.setString(2, aggType)
        stmt.setLong(3, sequenceNo)
        stmt.setString(4, eventType)
        stmt.setString(5, payload)
        stmt.executeUpdate()
      } finally stmt.close()
    } finally conn.close()
  }

  "PostgresJdbcEventStore" should "round-trip events ordered by sequence_no across saves" in withSync {
    val id = TestId("agg-1")
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id, 0L, Seq(TestEvent.Created("foundation")))
    }
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id, 1L, Seq(TestEvent.Updated(42)))
    }

    val stream = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(id)
    }

    stream.version shouldBe 2L
    stream.events should contain theSameElementsInOrderAs Seq(
      TestEvent.Created("foundation"),
      TestEvent.Updated(42)
    )
  }

  it should "return version 0 and no events for an unknown aggregate" in withSync {
    val stream = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(TestId("does-not-exist"))
    }

    stream.version shouldBe 0L
    stream.events shouldBe empty
  }

  it should "raise VersionConflict when two saves target the same sequence_no" in withSync {
    val id = TestId("agg-conflict")
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id, 0L, Seq(TestEvent.Created("first")))
    }

    val error = interceptRaised[EventStorePort.Error, Unit] {
      underTest.save(id, 0L, Seq(TestEvent.Created("second")))
    }

    error shouldBe EventStorePort.Error.VersionConflict(id)
  }

  it should "be a no-op when saving an empty event sequence" in withSync {
    val id = TestId("agg-empty")
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id, 0L, Seq.empty)
    }

    countEvents() shouldBe 0
    val stream = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(id)
    }
    stream.version shouldBe 0L
    stream.events shouldBe empty
  }

  it should "raise UnexpectedError when a stored payload cannot be decoded" in withSync {
    val id = TestId("agg-bad-payload")
    // Valid JSONB (passes the column constraint) but wrong shape for the TestEvent decoder.
    directInsert(id.value, aggregateType, 1L, "Created", """{"unexpected":true}""")

    val error = interceptRaised[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(id)
    }

    error shouldBe a[EventStorePort.Error.UnexpectedError]
  }

  it should "isolate events by aggregate_id" in withSync {
    val id1 = TestId("agg-a")
    val id2 = TestId("agg-b")
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id1, 0L, Seq(TestEvent.Created("a")))
    }
    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id2, 0L, Seq(TestEvent.Created("b")))
    }

    val stream1 = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(id1)
    }
    val stream2 = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      underTest.load(id2)
    }

    stream1.events shouldBe Seq(TestEvent.Created("a"))
    stream2.events shouldBe Seq(TestEvent.Created("b"))
  }

  it should "isolate events by aggregate_type" in withSync {
    val id    = TestId("shared-id")
    val other = PostgresJdbcEventStore[TestId, TestEvent](pool, "other-aggregate")

    failOnRaise[EventStorePort.Error, Unit] {
      underTest.save(id, 0L, Seq(TestEvent.Created("only-here")))
    }

    val otherStream = failOnRaise[EventStorePort.Error, EventStorePort.EventStream[TestEvent]] {
      other.load(id)
    }

    otherStream.version shouldBe 0L
    otherStream.events shouldBe empty
  }
}
