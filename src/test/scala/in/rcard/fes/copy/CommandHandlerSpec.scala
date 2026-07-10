package in.rcard.fes.copy

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.eventsourcing.EventStorePort
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.application.CopyCommandHandler
import in.rcard.fes.copy.domain.Command
import in.rcard.fes.copy.domain.CopyDecider
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.Domain.CopyState
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Event
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

  private val liftStoreError: EventStorePort.Error => Error = {
    case EventStorePort.Error.UnexpectedError(msg) => Error.UnexpectedError(msg)
    case EventStorePort.Error.VersionConflict(id)  => Error.UnexpectedError(s"Version conflict for copy: $id")
  }

  private val liftTerminalError: (CopyId, CopyState) => Error =
    (id, _) => Error.UnexpectedError(s"terminal: $id")

  private val registerCmd =
    Command.Register(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))

  "CommandHandler.apply" should "return the new event when handling a command for a new aggregate" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    val result = failOnRaise[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    result shouldBe Seq(
      Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
    )
  }

  it should "reconstruct state from existing events and raise AlreadyRegistered for duplicate" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    failOnRaise[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    error shouldBe Error.AlreadyRegistered(COPY_ID)
  }

  it should "propagate domain Error from decider without saving events" in withSync {
    val store   = InMemoryEventStore()
    val underTest = CommandHandler.apply(decider, store, liftStoreError, liftTerminalError)

    failOnRaise[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
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
    val underTest = CommandHandler.apply(decider, retryStore, liftStoreError, liftTerminalError)

    val result = failOnRaise[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    result shouldBe Seq(
      Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
    )
    saveCallCount shouldBe 2
  }

  it should "raise the lifted terminal error when the aggregate is terminal" in withSync {
    val terminalDecider = new Decider[Command, Event, CopyState, Error] {
      override def decide(command: Command, state: CopyState): Seq[Event] raises Error =
        Seq(Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR)))
      override def evolve(state: CopyState, event: Event): CopyState = state :+ event
      override val initialState: CopyState                           = CopyState.empty
      override def isTerminal(state: CopyState): Boolean             = true
    }
    val store = InMemoryEventStore()
    val liftTerminal: (CopyId, CopyState) => Error =
      (id, _) => Error.UnexpectedError(s"Copy '${id.value}' is in a terminal state")
    val underTest = CommandHandler.apply(terminalDecider, store, liftStoreError, liftTerminal)

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    error shouldBe Error.UnexpectedError(s"Copy '${COPY_ID.value}' is in a terminal state")
    store.savedEvents(COPY_ID) shouldBe Seq.empty
  }

  private val removedStreamEvents = Seq(
    Event.Registered(COPY_ID, FOUNDATION_ISBN, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR)),
    Event.Removed(COPY_ID)
  )

  private def storeWithRemovedCopy()(using Sync): InMemoryEventStore = {
    val store = InMemoryEventStore()
    failOnRaise[EventStorePort.Error, Unit] {
      store.save(COPY_ID, 0, removedStreamEvents)
    }
    store
  }

  private def rejectsCommandOnRemovedCopy(cmd: Command)(using Sync): Unit = {
    val store                    = storeWithRemovedCopy()
    given CopyDecider            = decider
    given EventStorePort[CopyId, Event] = store
    val underTest                = CopyCommandHandler.live

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(COPY_ID, cmd)
    }

    error shouldBe Error.CopyIsRemoved(COPY_ID)
    store.savedEvents(COPY_ID) shouldBe removedStreamEvents
  }

  "CopyCommandHandler" should "raise CopyIsRemoved for a Remove command on a removed copy" in withSync {
    rejectsCommandOnRemovedCopy(Command.Remove(COPY_ID))
  }

  it should "raise CopyIsRemoved for a MarkAsLost command on a removed copy" in withSync {
    rejectsCommandOnRemovedCopy(Command.MarkAsLost(COPY_ID))
  }

  it should "raise CopyIsRemoved for a MarkAsDamaged command on a removed copy" in withSync {
    rejectsCommandOnRemovedCopy(Command.MarkAsDamaged(COPY_ID))
  }

  it should "raise CopyIsRemoved for a Repair command on a removed copy" in withSync {
    rejectsCommandOnRemovedCopy(Command.Repair(COPY_ID))
  }

  it should "raise CopyIsRemoved for a Register command on a removed copy" in withSync {
    rejectsCommandOnRemovedCopy(registerCmd)
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
    val underTest = CommandHandler.apply(decider, brokenStore, liftStoreError, liftTerminalError)

    val error = interceptRaised[Error, Seq[Event]] {
      underTest.handle(COPY_ID, registerCmd)
    }

    error shouldBe Error.UnexpectedError("storage unavailable")
  }
}
