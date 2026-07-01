package in.rcard.fes.copy.adapter

import scala.language.implicitConversions
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.{MarkCopyAsDamagedError, MarkCopyAsDamagedUseCase}
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

trait MarkCopyAsDamagedRoute {
  val markCopyAsDamagedRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object MarkCopyAsDamagedRoute {

  def apply(markCopyAsDamagedUseCase: MarkCopyAsDamagedUseCase): MarkCopyAsDamagedRoute =
    new MarkCopyAsDamagedRoute {

      private def handlingDomainErrors(error: MarkCopyAsDamagedError): Response =
        error match
          case MarkCopyAsDamagedError.CopyNotFound(copyId) =>
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
          case MarkCopyAsDamagedError.AlreadyDamaged(copyId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Copy already damaged.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' is already damaged."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case MarkCopyAsDamagedError.CopyIsLost(copyId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Copy is lost.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' is lost and cannot be marked as damaged."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case MarkCopyAsDamagedError.UnexpectedError(_) =>
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

      override val markCopyAsDamagedRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/copies" / param[String]("id") / "damaged") { (req, id: String) =>
          Raise.recover {
            markCopyAsDamagedUseCase.markAsDamaged(CopyId(id))
            Response.ok[String]("Ok")
          } { (error: MarkCopyAsDamagedError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: MarkCopyAsDamagedUseCase): MarkCopyAsDamagedRoute =
    MarkCopyAsDamagedRoute(useCase)
}
