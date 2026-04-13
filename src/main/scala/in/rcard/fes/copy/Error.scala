package in.rcard.fes.copy

import in.rcard.fes.copy.Domain.CopyId

enum Error {
 case AlreadyRegistered(id: CopyId)
}