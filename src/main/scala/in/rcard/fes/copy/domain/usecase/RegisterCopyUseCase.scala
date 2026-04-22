package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Domain.{Author, CopyId, ISBN, Title}
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.fes.utils.reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.{Reader, reads}

trait RegisterCopyUseCase {
  def registerCopy(copyToRegister: CopyToRegister): CopyId
}
class RegisterCopyUseCaseLive(copyIdGenerator: CopyIdGenerator) extends RegisterCopyUseCase {
  override def registerCopy(copyToRegister: CopyToRegister): CopyId = {
    copyIdGenerator.generate()
  }
}
object RegisterCopyUseCase {
  given Reader[RegisterCopyUseCase] reads CopyIdGenerator = 
    reader(new RegisterCopyUseCaseLive(read[CopyIdGenerator]))

  case class CopyToRegister(isbn: ISBN, title: Title, author: Author)
}

trait CopyIdGenerator {
  def generate(): CopyId
}
class CopyIdGeneratorLive extends CopyIdGenerator {
  override def generate(): CopyId = CopyId("1")
}
object CopyIdGenerator {
  given Reader[CopyIdGenerator] = reader(new CopyIdGeneratorLive())
}
