package in.rcard.fes.copy.adapter

import scala.language.implicitConversions
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.{MarkCopyAsLostError, MarkCopyAsLostUseCase}
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.core.BodyEncoder
import in.rcard.yaes.http.core.Headers
import in.rcard.yaes.http.server.POST
import in.rcard.yaes.http.server.Response
import in.rcard.yaes.http.server.param
import in.rcard.yaes.http.server.p
import in.rcard.yaes.http.server.params.path.*
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route

trait MarkCopyAsLostRoute {
  val markCopyAsLostRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object MarkCopyAsLostRoute {

  def apply(markCopyAsLostUseCase: MarkCopyAsLostUseCase): MarkCopyAsLostRoute =
    new MarkCopyAsLostRoute {

      private def handlingDomainErrors(error: MarkCopyAsLostError): Response =
        error match
          case MarkCopyAsLostError.CopyNotFound(copyId) =>
            Response.notFound[ProblemDetailsDTO](
              ProblemDetailsDTO(
                title = "Not Found",
                detail = "The copy was not found.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' was not found."
                  )
                )
              )
            )
          case MarkCopyAsLostError.AlreadyLost(copyId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Copy already lost.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' is already lost."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case MarkCopyAsLostError.UnexpectedError(_) =>
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

      override val markCopyAsLostRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/copies" / param[String]("id") / "lost") { (req, id: String) =>
          Raise.recover {
            markCopyAsLostUseCase.markAsLost(CopyId(id))
            Response.ok[String]("Ok")
          } { (error: MarkCopyAsLostError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: MarkCopyAsLostUseCase): MarkCopyAsLostRoute = MarkCopyAsLostRoute(useCase)
}
