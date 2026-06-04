package in.rcard.fes.copy.adapter

import scala.language.implicitConversions
import in.rcard.fes.copy.adapter.RegisterCopyRoute.RegisterCopyDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.adapter.constraint.ISBN13
import in.rcard.fes.copy.application.{RegisterCopyError, RegisterCopyUseCase}
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.yaes.Raise
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
import io.circe.Encoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import in.rcard.yaes.Random
import in.rcard.yaes.Sync

trait RegisterCopyRoute {
  val registerCopyRoute: (Random, Sync) ?=> Route[NoParams, NoQueryParams]
}
object RegisterCopyRoute {

  case class RegisterCopyDTO(
      isbn: String :| ISBN13
  ) derives Decoder

  def apply(registerCopyUseCase: RegisterCopyUseCase): RegisterCopyRoute = new RegisterCopyRoute {

    private def handlingDomainErrors(error: RegisterCopyError): Response = {
      error match
        case RegisterCopyError.AlreadyRegistered(copyId) =>
          Response.withStatus(
            status = 409,
            value = ProblemDetailsDTO(
              title = "Conflict",
              detail = "Copy already registered.",
              errors = Seq(
                ErrorDTO(
                  detail = s"The copy with id '$copyId' is already registered."
                )
              )
            ),
            extraHeaders = Map(Headers.ContentType -> "application/json")
          )
        case RegisterCopyError.CopyNotFoundInCatalog(isbn) =>
          Response.badRequest[ProblemDetailsDTO](
            ProblemDetailsDTO(
              title = "Not Found",
              detail = "The requested ISBN was not found in the catalog.",
              errors = Seq(
                ErrorDTO(
                  detail = s"ISBN '${isbn.value}' not found in the catalog."
                )
              )
            )
          )
        case RegisterCopyError.UnexpectedError(_) =>
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

    override val registerCopyRoute: (Random, Sync) ?=> Route[NoParams, NoQueryParams] = POST(p"/copies") {
      req =>
        Raise.recover {
          val dto       = req.as[RegisterCopyDTO]
          val newCopyId = registerCopyUseCase.registerCopy(ISBN(dto.isbn))
          // FIXME The Created response should return the location of the created resource.
          //       Moreover we should have a ctor with the body and another one without it.
          Response.created[String]("Ok")
        } { (error: RegisterCopyError | DecodingError) =>
          error match {
            case decodingError: DecodingError      => handlingDecodingErrors(decodingError)
            case error: RegisterCopyError          => handlingDomainErrors(error)
          }
        }
    }
  }

  given live(using useCase: RegisterCopyUseCase): RegisterCopyRoute = RegisterCopyRoute(useCase)
}
