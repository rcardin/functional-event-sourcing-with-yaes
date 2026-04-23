package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Domain.{Author, CopyId, ISBN, Title}
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.fes.utils.reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.{Reader, reads}

trait RegisterCopyUseCase {
  def registerCopy(copyToRegister: CopyToRegister): CopyId
}
object RegisterCopyUseCase {
  case class CopyToRegister(isbn: ISBN, title: Title, author: Author)

  def apply(copyIdGenerator: CopyIdGenerator): RegisterCopyUseCase = 
    (copyToRegister: CopyToRegister) => copyIdGenerator.generate()
  
  given Reader[RegisterCopyUseCase] reads CopyIdGenerator = 
    reader(RegisterCopyUseCase(read[CopyIdGenerator]))
}

trait CopyIdGenerator {
  def generate(): CopyId
}
object CopyIdGenerator {
  
  def apply(): CopyIdGenerator = () => CopyId("1")
  
  given Reader[CopyIdGenerator] = reader(CopyIdGenerator())
}
