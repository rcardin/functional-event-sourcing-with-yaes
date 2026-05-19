package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.utils.{RandomSpec, SyncSpec}
import in.rcard.yaes.{Raise, Random, raises}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.Sync
import in.rcard.yaes.test.scalatest.RaiseSpec

class RegisterCopyUseCaseSpec
    extends AnyFlatSpec
    with SyncSpec
    with RaiseSpec
    with RandomSpec
    with Matchers {
  val mockCopyIdGenerator: CopyIdGenerator = new CopyIdGenerator {
    override def generate()(using Random): CopyId = COPY_ID
  }
  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command): Seq[Event] raises Error = cmd match {
        case Command.Register(copyId, isbn, title, authors) if isbn == ALREADY_REGISTERED_ISBN =>
          Raise.raise(Error.AlreadyRegistered(copyId))
        case Command.Register(copyId, isbn, title, authors) if isbn == FOUNDATION_ISBN =>
          Seq(Event.Registered(copyId, isbn, title, authors))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected state after copy registration"))
      }
    }

  val mockFindCopyByIsbnPort: FindCopyByIsbnPort = new FindCopyByIsbnPort {
    override def find(isbn: ISBN)(using Sync): CopyToRegister raises FindCopyByIsbnPort.Error =
      isbn match {
        case NOT_IN_CATALOG_ISBN => Raise.raise(FindCopyByIsbnPort.Error.NotFound(isbn))
        case CATALOG_ERROR_ISBN  =>
          Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError("Catalog error"))
        case _ => CopyToRegister(isbn, FOUNDATION_TITLE, Seq(FOUNDATION_AUTHOR))
      }
  }

  private val underTest =
    RegisterCopyUseCase(mockCopyIdGenerator, mockCommandHandler, mockFindCopyByIsbnPort)

  "RegisterCopyUseCase.registerCopy" should "register a copy successfully if it is not already registered" in withSync {
    val actualResult = failOnRaise { underTest.registerCopy(FOUNDATION_ISBN) }

    actualResult shouldBe COPY_ID
  }

  it should "not register a copy if it is already registered" in withSync {

    RandomStub.nextInts(1, 2, 3)

    val actualResult = interceptRaised { underTest.registerCopy(ALREADY_REGISTERED_ISBN) }

    actualResult shouldBe Error.AlreadyRegistered(COPY_ID)
  }

  it should "raise an error if the command handler returns an unexpected event" in withSync {
    val actualResult = interceptRaised { underTest.registerCopy(UNEXPECTED_ISBN) }

    actualResult shouldBe Error.UnexpectedError("Unexpected state after copy registration")
  }

  it should "raise an error if the ISBN is not found in the catalog" in withSync {
    val actualResult = interceptRaised { underTest.registerCopy(NOT_IN_CATALOG_ISBN) }

    actualResult shouldBe Error.UnexpectedError(
      s"ISBN not found in catalog: ${NOT_IN_CATALOG_ISBN.value}"
    )
  }

  it should "raise an error if there is an unexpected error from the catalog" in withSync {
    val actualResult = interceptRaised { underTest.registerCopy(CATALOG_ERROR_ISBN) }

    actualResult shouldBe Error.UnexpectedError("Catalog error")
  }
}
