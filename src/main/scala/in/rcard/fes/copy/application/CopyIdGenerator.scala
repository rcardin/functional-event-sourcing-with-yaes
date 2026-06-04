package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId
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
