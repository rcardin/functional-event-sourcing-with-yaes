package in.rcard.fes

object Events {
  sealed trait Event
  case class SolutionQuoted()    extends Event
  case class SolutionReserved()  extends Event
  case class SolutionConfirmed() extends Event
}
