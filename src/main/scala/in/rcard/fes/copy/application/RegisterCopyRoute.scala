package in.rcard.fes.copy.application

import in.rcard.fes.copy.application.RegisterCopyRoute.RegisterCopyDTO
import in.rcard.fes.copy.application.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.application.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.constraint.ISBN13
import in.rcard.fes.copy.domain.Domain.Author
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.Domain.Title
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Error.UnexpectedError
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.Raise
import in.rcard.yaes.Reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.Reader.reader
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.core.BodyEncoder
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.http.core.DecodingError.ParseError
import in.rcard.yaes.http.core.DecodingError.ValidationError
import in.rcard.yaes.http.core.Headers
import in.rcard.yaes.http.server.POST
import in.rcard.yaes.http.server.Response
import in.rcard.yaes.http.server.p
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import in.rcard.yaes.reads
import io.circe.Decoder
import io.circe.Encoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*

trait RegisterCopyRoute {
  val registerCopyRoute: Route[NoParams, NoQueryParams]
}
object RegisterCopyRoute {

  case class RegisterCopyDTO(
      isbn: String :| ISBN13,
      title: String :| Not[Blank],
      author: String :| Not[Blank]
  ) derives Decoder

  def apply(registerCopyUseCase: RegisterCopyUseCase): RegisterCopyRoute = new RegisterCopyRoute {

    private def handlingDomainErrors(error: Error): Response = {
      error match
        case Error.AlreadyRegistered(copyId) =>
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
        case Error.UnexpectedError(_) =>
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
    private def handlingDecodingErrors(errors: List[DecodingError]): Response = errors match {
      case Nil        => throw new IllegalArgumentException("This case can't happen")
      case pe :: tail =>
        pe match {
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
          case _ =>
            Response.badRequest[ProblemDetailsDTO](
              ProblemDetailsDTO(
                title = "Validation error",
                detail = "The request body is not valid. Please check the errors for more details.",
                errors = Seq(
                  ErrorDTO(
                    detail = errors.map(_.message).mkString(", ")
                  )
                )
              )
            )
        }
    }

    override val registerCopyRoute: Route[NoParams, NoQueryParams] = POST(p"/copies") { req =>
      Raise.recover {
        val dto       = req.as[RegisterCopyDTO]
        val newCopyId = registerCopyUseCase.registerCopy(
          RegisterCopyUseCase.CopyToRegister(
            isbn = ISBN(dto.isbn),
            title = Title(dto.title),
            author = Author(dto.author)
          )
        )
        // FIXME The Created response should return the location of the created resource.
        //       Moreover we should have a ctor with the body and another one without it.
        Response.created[String]("Ok")
      } { error =>
        error match {
          case decodingErrors: List[DecodingError] => handlingDecodingErrors(decodingErrors)
          case error: Error                        => handlingDomainErrors(error)
        }
      }
    }
  }

  given live: Reader[RegisterCopyRoute] reads RegisterCopyUseCase = reader(
    RegisterCopyRoute(read[RegisterCopyUseCase])
  )
}
