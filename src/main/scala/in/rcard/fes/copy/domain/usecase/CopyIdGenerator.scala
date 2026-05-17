package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Random

trait CopyIdGenerator {
  def generate()(using Random): CopyId
}

object CopyIdGenerator {

  def apply(): CopyIdGenerator = new CopyIdGenerator {
    override def generate()(using Random): CopyId = CopyId(Random.nextUuid)
  }

  given live: CopyIdGenerator = CopyIdGenerator()
}