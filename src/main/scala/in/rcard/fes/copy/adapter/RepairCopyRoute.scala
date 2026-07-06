package in.rcard.fes.copy.adapter

import scala.language.implicitConversions
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.application.{RepairCopyError, RepairCopyUseCase}
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

trait RepairCopyRoute {
  val repairCopyRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object RepairCopyRoute {

  def apply(repairCopyUseCase: RepairCopyUseCase): RepairCopyRoute =
    new RepairCopyRoute {

      private def handlingDomainErrors(error: RepairCopyError): Response =
        error match
          case RepairCopyError.CopyNotFound(copyId) =>
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
          case RepairCopyError.NotDamaged(copyId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Copy is not damaged.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The copy with id '${copyId.value}' is not damaged and cannot be repaired."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case RepairCopyError.UnexpectedError(_) =>
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

      override val repairCopyRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/copies" / param[String]("id") / "repaired") { (req, id: String) =>
          Raise.recover {
            repairCopyUseCase.repair(CopyId(id))
            Response.ok[String]("Ok")
          } { (error: RepairCopyError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: RepairCopyUseCase): RepairCopyRoute =
    RepairCopyRoute(useCase)
}
