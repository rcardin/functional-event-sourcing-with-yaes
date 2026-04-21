package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Domain.{Author, CopyId, ISBN, Title}
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.yaes
import in.rcard.yaes.{Reader, reads}
import io.github.iltotore.iron.autoRefine

trait RegisterCopyUseCase {
  def registerCopy(copyToRegister: CopyToRegister): CopyId
}
object RegisterCopyUseCase {
  val live: RegisterCopyUseCase reads CopyIdGenerator = new RegisterCopyUseCase {
    override def registerCopy(copyToRegister: CopyToRegister): CopyId = {
      val copyIdGenerator = Reader.read

      copyIdGenerator.generate()
    }
  }

  case class CopyToRegister(isbn: ISBN, title: Title, author: Author)
}

trait CopyIdGenerator {
  def generate(): CopyId
}
object CopyIdGenerator {
  val live: CopyIdGenerator = () => CopyId("1")
}
