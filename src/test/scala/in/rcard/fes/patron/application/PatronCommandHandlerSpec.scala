package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.eventsourcing.EventStorePort
import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.application.PatronCommandHandler
import in.rcard.fes.patron.domain.Command
import in.rcard.fes.patron.domain.PatronDecider
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.Domain.PatronState
import in.rcard.fes.patron.domain.Error
import in.rcard.fes.patron.domain.Event
import in.rcard.fes.patron.infrastructure.PatronPostgresEventStore.patronIdValuable
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PatronCommandHandlerSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  class InMemoryEventStore extends EventStorePort[PatronId, Event] {
    private var store: Map[PatronId, (Long, Seq[Event])] = Map.empty

    def savedEvents(id: PatronId): Seq[Event] = store.get(id).map(_._2).getOrElse(Seq.empty)

    override def load(
        id: PatronId
    )(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error =
      store.get(id) match {
        case Some((version, events)) => EventStorePort.EventStream(version, events)
        case None                    => EventStorePort.EventStream(0, Seq.empty)
      }

    override def save(id: PatronId, expectedVersion: Long, events: Seq[Event])(using
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

  private val decider = PatronDecider()

  private val liftStoreError: EventStorePort.Error => Error = {
    case EventStorePort.Error.UnexpectedError(msg) => Error.UnexpectedError(msg)
    case EventStorePort.Error.VersionConflict(id)  => Error.UnexpectedError(s"Version conflict for patron: $id")
  }

  private val liftTerminalError: (PatronId, PatronState) => Error =
    (id, _) => Error.UnexpectedError(s"terminal: $id")

  private val registerCmd =
    Command.Register(CARD_ID, PATRON_NAME, BORROW_LIMIT)

  "CommandHandler.apply" should "return the new event when handling a command for a new aggregate" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    val result = failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    result shouldBe Seq(
      Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)
    )
  }

  it should "reconstruct state from existing events and raise AlreadyRegistered for duplicate" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    error shouldBe Error.AlreadyRegistered(CARD_ID)
  }

  it should "propagate domain Error from decider without saving events" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    error shouldBe Error.AlreadyRegistered(CARD_ID)
    store.savedEvents(CARD_ID) shouldBe Seq(
      Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)
    )
  }

  it should "retry on VersionConflict and succeed on second attempt" in withSync {
    var saveCallCount = 0
    val retryStore    = new EventStorePort[PatronId, Event] {
      override def load(id: PatronId)(using
          Sync
      ): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        EventStorePort.EventStream(0, Seq.empty)
      override def save(id: PatronId, expectedVersion: Long, events: Seq[Event])(using
          Sync
      ): Unit raises EventStorePort.Error = {
        saveCallCount += 1
        if saveCallCount == 1 then Raise.raise(EventStorePort.Error.VersionConflict(id))
      }
    }
    val underTest = CommandHandler.apply(decider, retryStore, liftStoreError, liftTerminalError)

    val result = failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    result shouldBe Seq(
      Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)
    )
    saveCallCount shouldBe 2
  }

  it should "raise the lifted terminal error when the aggregate is terminal" in withSync {
    val terminalDecider = new Decider[Command, Event, PatronState, Error] {
      override def decide(command: Command, state: PatronState): Seq[Event] raises Error =
        Seq(Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT))
      override def evolve(state: PatronState, event: Event): PatronState = state :+ event
      override val initialState: PatronState                            = PatronState.empty
      override def isTerminal(state: PatronState): Boolean              = true
    }
    val store = InMemoryEventStore()
    val liftTerminal: (PatronId, PatronState) => Error =
      (id, _) => Error.UnexpectedError(s"Patron '${id.value}' is in a terminal state")
    val underTest = CommandHandler.apply(terminalDecider, store, liftStoreError, liftTerminal)

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    error shouldBe Error.UnexpectedError(s"Patron '${CARD_ID.value}' is in a terminal state")
    store.savedEvents(CARD_ID) shouldBe Seq.empty
  }

  it should "propagate UnexpectedError when load raises it" in withSync {
    val brokenStore = new EventStorePort[PatronId, Event] {
      override def load(id: PatronId)(using
          Sync
      ): EventStorePort.EventStream[Event] raises EventStorePort.Error =
        Raise.raise(EventStorePort.Error.UnexpectedError("storage unavailable"))
      override def save(id: PatronId, expectedVersion: Long, events: Seq[Event])(using
          Sync
      ): Unit raises EventStorePort.Error =
        ()
    }
    val underTest = CommandHandler.apply(decider, brokenStore, liftStoreError, liftTerminalError)

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    error shouldBe Error.UnexpectedError("storage unavailable")
  }

  "PatronCommandHandler" should "register a new patron via the live handler" in withSync {
    val store                             = InMemoryEventStore()
    given PatronDecider                   = decider
    given EventStorePort[PatronId, Event] = store
    val underTest                         = PatronCommandHandler.live

    val result = failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    result shouldBe Seq(Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT))
    store.savedEvents(CARD_ID) shouldBe Seq(Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT))
  }

  it should "raise AlreadyRegistered for a duplicate Register via the live handler" in withSync {
    val store                             = InMemoryEventStore()
    given PatronDecider                   = decider
    given EventStorePort[PatronId, Event] = store
    val underTest                         = PatronCommandHandler.live

    failOnRaise[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(CARD_ID, registerCmd)
    }

    error shouldBe Error.AlreadyRegistered(CARD_ID)
    store.savedEvents(CARD_ID) shouldBe Seq(Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT))
  }
}
