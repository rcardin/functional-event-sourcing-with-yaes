package in.rcard.fes

object Commands {
  sealed trait Command
  case class Quote() extends Command
  case class Reserve() extends Command
  case class Confirm() extends Command
}
