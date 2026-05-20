package in.rcard.fes.copy

import in.rcard.fes.{CommandHandler, EventStorePort}
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.CopyDecider
import in.rcard.fes.utils.SyncSpec
import in.rcard.yaes.{Raise, Sync, raises}
import in.rcard.yaes.test.scalatest.RaiseSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandHandlerSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  class InMemoryEventStore extends EventStorePort[CopyId, Event] {
    private var store: Map[CopyId, (Long, Seq[Event])] = Map.empty

    def savedEvents(id: CopyId): Seq[Event] = store.get(id).map(_._2).getOrElse(Seq.empty)

    override def load(id: CopyId)(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error =
      store.get(id) match {
        case Some((version, events)) => EventStorePort.EventStream(version, events)
        case None                    => EventStorePort.EventStream(0, Seq.empty)
      }

    override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using Sync): Unit raises EventStorePort.Error = {
      val currentVersion = store.get(id).map(_._1).getOrElse(0L)
      if currentVersion != expectedVersion then Raise.raise(EventStorePort.Error.VersionConflict(id))
      else
        val existing = store.get(id).map(_._2).getOrElse(Seq.empty)
        store = store.updated(id, (expectedVersion + events.size, existing ++ events))
    }
  }

  private val decider = CopyDecider()

  private val registerCmd = Command.Register(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))

  "CommandHandler.apply" should "return the new event when handling a command for a new aggregate" in withSync {
    val store   = InMemoryEventStore()
    val handler = CommandHandler.apply(decider, store)

    val result = failOnRaise[Error, Seq[Event]] {
      Raise.recover {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore raised unexpected error: $err") }
    }

    result shouldBe Seq(Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR)))
  }

  it should "reconstruct state from existing events and raise AlreadyRegistered for duplicate" in withSync {
    val store   = InMemoryEventStore()
    val handler = CommandHandler.apply(decider, store)

    failOnRaise[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore error on first registration: $err") }
    }

    val error = interceptRaised[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore error on second registration: $err") }
    }

    error shouldBe Error.AlreadyRegistered(COPY_ID)
  }

  it should "propagate domain Error from decider without saving events" in withSync {
    val store   = InMemoryEventStore()
    val handler = CommandHandler.apply(decider, store)

    failOnRaise[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore error: $err") }
    }

    val error = interceptRaised[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore error: $err") }
    }

    error shouldBe Error.AlreadyRegistered(COPY_ID)
    store.savedEvents(COPY_ID) shouldBe Seq(
      Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
    )
  }

  it should "propagate VersionConflict when save raises it" in withSync {
    val conflictStore = new EventStorePort[CopyId, Event] {
      override def load(id: CopyId)(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        EventStorePort.EventStream(0, Seq.empty)
      override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using Sync): Unit raises EventStorePort.Error =
        Raise.raise(EventStorePort.Error.VersionConflict(id))
    }
    val handler = CommandHandler.apply(decider, conflictStore)

    val error = interceptRaised[EventStorePort.Error, Seq[Event]] {
      failOnRaise[Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      }
    }

    error shouldBe EventStorePort.Error.VersionConflict(COPY_ID)
  }

  it should "propagate UnexpectedError when load raises it" in withSync {
    val brokenStore = new EventStorePort[CopyId, Event] {
      override def load(id: CopyId)(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        Raise.raise(EventStorePort.Error.UnexpectedError("storage unavailable"))
      override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using Sync): Unit raises EventStorePort.Error =
        ()
    }
    val handler = CommandHandler.apply(decider, brokenStore)

    val error = interceptRaised[EventStorePort.Error, Seq[Event]] {
      failOnRaise[Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      }
    }

    error shouldBe EventStorePort.Error.UnexpectedError("storage unavailable")
  }
}
