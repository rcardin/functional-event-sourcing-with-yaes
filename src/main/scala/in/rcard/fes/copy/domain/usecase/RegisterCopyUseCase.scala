package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Command.Register
import in.rcard.fes.copy.domain.Domain.CopyId

trait RegisterCopyUseCase {
  def registerCopy(registerCmd: Register): CopyId
}
