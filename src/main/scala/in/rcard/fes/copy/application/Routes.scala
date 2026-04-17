package in.rcard.fes.copy.application

import in.rcard.fes.copy.application.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.constraint.ISBN13
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.http.core.DecodingError.{ParseError, ValidationError}
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import in.rcard.yaes.http.server.{POST, Response, p}
import in.rcard.yaes.{Raise, Reader}
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*

object Routes {

  // The class is a super simplified version of the RFC 9457 Problem Details for HTTP APIs.
  case class ProblemDetailsDTO(
      title: String,
      detail: String,
      errors: Seq[ErrorDTO]
  ) derives Encoder.AsObject,
        Decoder
  object ProblemDetailsDTO {
    case class ErrorDTO(
        detail: String
    ) derives Encoder.AsObject,
          Decoder
  }

  trait RegisterCopyRoute {
    val registerCopyRoute: Route[NoParams, NoQueryParams]
  }
  object RegisterCopyRoute {

    case class RegisterCopyDTO(
        isbn: String :| ISBN13,
        title: String :| Not[Blank],
        author: String :| Not[Blank]
    ) derives Encoder.AsObject,
          Decoder

    def apply()(using Reader[RegisterCopyUseCase]): RegisterCopyRoute = new RegisterCopyRoute {

      private val registerCopyUseCase = Reader.read

      val registerCopyRoute: Route[NoParams, NoQueryParams] = POST(p"/copies") { req =>
        Raise.recover {
          val dto = req.as[RegisterCopyDTO]
          // FIXME The Created response should return the location of the created resource.
          //       Moreover we should have a ctor with the body and another one without it.
          Response.created[String]("Ok")
        } {
          case error: ParseError =>
            Response.badRequest[ProblemDetailsDTO](
              ProblemDetailsDTO(
                title = "Invalid request body",
                detail = "The request body could not be parsed. Please check the syntax.",
                errors = Seq(
                  ErrorDTO(
                    detail = error.message
                  )
                )
              )
            )
          case error: ValidationError =>
            Response.badRequest[ProblemDetailsDTO](
              ProblemDetailsDTO(
                title = "Validation error",
                detail = "The request body is not valid. Please check the errors for more details.",
                errors = Seq(
                  ErrorDTO(
                    detail = error.message
                  )
                )
              )
            )
        }
      }
    }
  }
}
