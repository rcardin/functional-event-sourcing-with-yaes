package in.rcard.fes.copy

import in.rcard.fes.CommandHandler
import in.rcard.fes.Decider
import in.rcard.fes.EventStorePort
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Command
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.Domain.CopyState
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Event
import in.rcard.fes.copy.domain.usecase.CopyDecider
import in.rcard.fes.copy.infrastructure.CopyPostgresEventStore.copyIdValuable
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandHandlerSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  class InMemoryEventStore extends EventStorePort[CopyId, Event] {
    private var store: Map[CopyId, (Long, Seq[Event])] = Map.empty

    def savedEvents(id: CopyId): Seq[Event] = store.get(id).map(_._2).getOrElse(Seq.empty)

    override def load(
        id: CopyId
    )(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error =
      store.get(id) match {
        case Some((version, events)) => EventStorePort.EventStream(version, events)
        case None                    => EventStorePort.EventStream(0, Seq.empty)
      }

    override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using
        Sync
    ): Unit raises EventStorePort.Error = {
      val currentVersion = store.get(id).map(_._1).getOrElse(0L)
      if currentVersion != expectedVersion then
        Raise.raise(EventStorePort.Error.VersionConflict(id))
      else
        val existing = store.get(id).map(_._2).getOrElse(Seq.empty)
        store = store.updated(id, (expectedVersion + events.size, existing ++ events))
    }
  }

  private val decider = CopyDecider()

  private val registerCmd =
    Command.Register(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))

  "CommandHandler.apply" should "return the new event when handling a command for a new aggregate" in withSync {
    val store   = InMemoryEventStore()
    val handler = CommandHandler.apply(decider, store)

    val result = failOnRaise[Error, Seq[Event]] {
      Raise.recover {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore raised unexpected error: $err") }
    }

    result shouldBe Seq(
      Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
    )
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

  it should "retry on VersionConflict and succeed on second attempt" in withSync {
    var saveCallCount = 0
    val retryStore    = new EventStorePort[CopyId, Event] {
      override def load(id: CopyId)(using
          Sync
      ): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        EventStorePort.EventStream(0, Seq.empty)
      override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using
          Sync
      ): Unit raises EventStorePort.Error = {
        saveCallCount += 1
        if saveCallCount == 1 then Raise.raise(EventStorePort.Error.VersionConflict(id))
      }
    }
    val handler = CommandHandler.apply(decider, retryStore)

    val result = failOnRaise[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore raised unexpected error: $err") }
    }

    result shouldBe Seq(
      Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
    )
    saveCallCount shouldBe 2
  }

  it should "return empty events when aggregate is terminal" in withSync {
    val terminalDecider = new Decider[Command, Event, CopyState, Error] {
      override def decide(command: Command, state: CopyState): Seq[Event] raises Error =
        Seq(Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR)))
      override def evolve(state: CopyState, event: Event): CopyState = state :+ event
      override val initialState: CopyState                           = CopyState.empty
      override def isTerminal(state: CopyState): Boolean             = true
    }
    val store   = InMemoryEventStore()
    val handler = CommandHandler.apply(terminalDecider, store)

    val result = failOnRaise[Error, Seq[Event]] {
      Raise.recover[EventStorePort.Error, Seq[Event]] {
        handler.handle(COPY_ID, registerCmd)
      } { err => fail(s"EventStore raised unexpected error: $err") }
    }

    result shouldBe Seq.empty
    store.savedEvents(COPY_ID) shouldBe Seq.empty
  }

  it should "propagate UnexpectedError when load raises it" in withSync {
    val brokenStore = new EventStorePort[CopyId, Event] {
      override def load(id: CopyId)(using
          Sync
      ): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        Raise.raise(EventStorePort.Error.UnexpectedError("storage unavailable"))
      override def save(id: CopyId, expectedVersion: Long, events: Seq[Event])(using
          Sync
      ): Unit raises EventStorePort.Error =
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
