package in.rcard.fes.patron.adapter

import scala.language.implicitConversions
import in.rcard.fes.patron.adapter.RegisterPatronRoute.RegisterPatronDTO
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.patron.application.{RegisterPatronError, RegisterPatronUseCase}
import in.rcard.fes.patron.domain.Domain.{BorrowLimit, PatronId, PatronName}
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.core.BodyEncoder
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.http.core.DecodingError.ParseError
import in.rcard.yaes.http.core.DecodingError.ValidationErrors
import in.rcard.yaes.http.core.Headers
import in.rcard.yaes.http.server.POST
import in.rcard.yaes.http.server.Response
import in.rcard.yaes.http.server.p
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import io.circe.Decoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*

trait RegisterPatronRoute {
  val registerPatronRoute: Sync ?=> Route[NoParams, NoQueryParams]
}
object RegisterPatronRoute {

  case class RegisterPatronDTO(
      cardId: String :| Not[Empty],
      name: String :| Not[Empty],
      borrowLimit: Int :| Interval.Closed[1, 10]
  ) derives Decoder

  def apply(registerPatronUseCase: RegisterPatronUseCase): RegisterPatronRoute = new RegisterPatronRoute {

    private def handlingDomainErrors(error: RegisterPatronError): Response = {
      error match
        case RegisterPatronError.AlreadyRegistered(patronId) =>
          Response.withStatus(
            status = 409,
            value = ProblemDetailsDTO(
              title = "Conflict",
              detail = "Patron already registered.",
              errors = Seq(
                ErrorDTO(
                  detail = s"The patron with card id '${patronId.value}' is already registered."
                )
              )
            ),
            extraHeaders = Map(Headers.ContentType -> "application/json")
          )
        case RegisterPatronError.UnexpectedError(_) =>
          Response.internalServerError(
            ProblemDetailsDTO(
              title = "Unexpected error",
              detail = "An unexpected error occurred.",
              errors = Seq(
                ErrorDTO(
                  detail = "Unexpected error"
                )
              )
            )
          )
    }

    private def handlingDecodingErrors(error: DecodingError): Response = error match {

      case ParseError(message, cause) =>
        Response.badRequest[ProblemDetailsDTO](
          ProblemDetailsDTO(
            title = "Invalid request body",
            detail = "The request body could not be parsed. Please check the syntax.",
            errors = Seq(
              ErrorDTO(
                detail = message
              )
            )
          )
        )
      case ValidationErrors(validationErrors) =>
        Response.badRequest[ProblemDetailsDTO](
          ProblemDetailsDTO(
            title = "Validation error",
            detail = "The request body is not valid. Please check the errors for more details.",
            errors = validationErrors.map { error =>
              ErrorDTO(
                detail = error
              )
            }.toList
          )
        )
    }

    override val registerPatronRoute: Sync ?=> Route[NoParams, NoQueryParams] = POST(p"/patrons") { req =>
      Raise.recover {
        val dto          = req.as[RegisterPatronDTO]
        val newPatronId  = registerPatronUseCase.registerPatron(
          PatronId(dto.cardId),
          PatronName(dto.name),
          BorrowLimit(dto.borrowLimit)
        )
        Response.created[String]("Created", Map("Location" -> s"/patrons/${dto.cardId}"))
      } { (error: RegisterPatronError | DecodingError) =>
        error match {
          case decodingError: DecodingError => handlingDecodingErrors(decodingError)
          case error: RegisterPatronError   => handlingDomainErrors(error)
        }
      }
    }
  }

  given live(using useCase: RegisterPatronUseCase): RegisterPatronRoute = RegisterPatronRoute(useCase)
}
