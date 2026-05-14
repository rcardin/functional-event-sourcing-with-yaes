package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.fes.utils.RaiseSpec
import in.rcard.yaes.{Raise, raises}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegisterCopyUseCaseSpec extends AnyFlatSpec with RaiseSpec with Matchers {
  val mockCopyIdGenerator: CopyIdGenerator                              = () => COPY_ID
  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command): Seq[Event] raises Error = cmd match {
        case Command.Register(copyId, isbn, title, author) if isbn == ALREADY_REGISTERED_ISBN =>
          Raise.raise(Error.AlreadyRegistered(copyId))
        case Command.Register(copyId, isbn, title, author) if isbn == FOUNDATION_ISBN =>
          Seq(Event.Registered(copyId, isbn, title, author))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected state after copy registration"))
      }
    }

  val mockFindCopyByIsbnPort: FindCopyByIsbnPort = new FindCopyByIsbnPort {
    override def find(isbn: ISBN): CopyToRegister raises FindCopyByIsbnPort.Error = isbn match {
      case NOT_IN_CATALOG_ISBN => Raise.raise(FindCopyByIsbnPort.Error.NotFound(isbn))
      case CATALOG_ERROR_ISBN  => Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError("Catalog error"))
      case _                   => CopyToRegister(isbn, FOUNDATION_TITLE, FOUNDATION_AUTHOR)
    }
  }

  private val underTest =
    RegisterCopyUseCase(mockCopyIdGenerator, mockCommandHandler, mockFindCopyByIsbnPort)

  "RegisterCopyUseCase.registerCopy" should "register a copy successfully if it is not already registered" in {
    val copyToRegister = RegisterCopyUseCase.CopyToRegister(
      FOUNDATION_ISBN,
      FOUNDATION_TITLE,
      FOUNDATION_AUTHOR
    )

    val actualResult = failOnRaise { underTest.registerCopy(copyToRegister) }

    actualResult shouldBe COPY_ID
  }

  it should "not register a copy if it is already registered" in {
    val copyToRegister = RegisterCopyUseCase.CopyToRegister(
      ALREADY_REGISTERED_ISBN,
      FOUNDATION_TITLE,
      FOUNDATION_AUTHOR
    )

    val actualResult = interceptRaised { underTest.registerCopy(copyToRegister) }

    actualResult shouldBe Error.AlreadyRegistered(COPY_ID)
  }

  it should "raise an error if the command handler returns an unexpected event" in {
    val copyToRegister = RegisterCopyUseCase.CopyToRegister(
      UNEXPECTED_ISBN,
      FOUNDATION_TITLE,
      FOUNDATION_AUTHOR
    )

    val actualResult = interceptRaised { underTest.registerCopy(copyToRegister) }

    actualResult shouldBe Error.UnexpectedError("Unexpected state after copy registration")
  }

  it should "raise an error if the ISBN is not found in the catalog" in {
    val copyToRegister = RegisterCopyUseCase.CopyToRegister(
      NOT_IN_CATALOG_ISBN,
      FOUNDATION_TITLE,
      FOUNDATION_AUTHOR
    )

    val actualResult = interceptRaised { underTest.registerCopy(copyToRegister) }

    actualResult shouldBe Error.UnexpectedError(
      s"ISBN not found in catalog: ${NOT_IN_CATALOG_ISBN.value}"
    )
  }

  it should "raise an error if there is an unexpected error from the catalog" in {
    val copyToRegister = RegisterCopyUseCase.CopyToRegister(
      CATALOG_ERROR_ISBN,
      FOUNDATION_TITLE,
      FOUNDATION_AUTHOR
    )

    val actualResult = interceptRaised { underTest.registerCopy(copyToRegister) }

    actualResult shouldBe Error.UnexpectedError("Catalog error")
  }
}
