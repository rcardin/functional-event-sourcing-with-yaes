package in.rcard.fes

import in.rcard.fes.utils.reader
import in.rcard.yaes.{Reader, raises}

trait CommandHandler[Id, Command, Error, Event] {
  def handle(id: Id, cmd: Command): Seq[Event] raises Error
}
object CommandHandler {
  def apply[Id, Command, Error, Event](): CommandHandler[Id, Command, Error, Event] =
    new CommandHandler[Id, Command, Error, Event] {
      override def handle(id: Id, cmd: Command): Seq[Event] raises Error = Seq.empty
    }

  // FIXME ?
  given [Id, Command, Error, Event] => Reader[CommandHandler[Id, Command, Error, Event]] = reader(
    CommandHandler()
  )
}
