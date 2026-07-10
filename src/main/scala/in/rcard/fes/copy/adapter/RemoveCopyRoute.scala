package in.rcard.fes.copy.adapter

import scala.language.implicitConversions
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.{RemoveCopyError, RemoveCopyUseCase}
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

trait RemoveCopyRoute {
  val removeCopyRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object RemoveCopyRoute {

  def apply(removeCopyUseCase: RemoveCopyUseCase): RemoveCopyRoute =
    new RemoveCopyRoute {

      private def handlingDomainErrors(error: RemoveCopyError): Response =
        error match
          case RemoveCopyError.CopyNotFound(copyId) =>
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
          case RemoveCopyError.CopyIsRemoved(copyId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Copy is already removed.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' was already removed from the catalog."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case RemoveCopyError.UnexpectedError(_) =>
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

      override val removeCopyRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/copies" / param[String]("id") / "removed") { (req, id: String) =>
          Raise.recover {
            removeCopyUseCase.remove(CopyId(id))
            Response.ok[String]("Ok")
          } { (error: RemoveCopyError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: RemoveCopyUseCase): RemoveCopyRoute =
    RemoveCopyRoute(useCase)
}
