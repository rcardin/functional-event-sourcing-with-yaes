package in.rcard.fes.patron.adapter

import scala.language.implicitConversions
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.patron.application.{SuspendPatronError, SuspendPatronUseCase}
import in.rcard.fes.patron.domain.Domain.PatronId
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

trait SuspendPatronRoute {
  val suspendPatronRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object SuspendPatronRoute {

  def apply(suspendPatronUseCase: SuspendPatronUseCase): SuspendPatronRoute =
    new SuspendPatronRoute {

      private def handlingDomainErrors(error: SuspendPatronError): Response =
        error match
          case SuspendPatronError.PatronNotFound(patronId) =>
            Response.notFound[ProblemDetailsDTO](
              ProblemDetailsDTO(
                title = "Not Found",
                detail = "The patron was not found.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The patron with card id '${patronId.value}' was not found."
                  )
                )
              )
            )
          case SuspendPatronError.AlreadySuspended(patronId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Patron already suspended.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The patron with card id '${patronId.value}' is already suspended."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case SuspendPatronError.UnexpectedError(_) =>
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

      override val suspendPatronRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/patrons" / param[String]("cardId") / "suspended") { (req, cardId: String) =>
          Raise.recover {
            suspendPatronUseCase.suspend(PatronId(cardId))
            Response.ok[String]("Ok")
          } { (error: SuspendPatronError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: SuspendPatronUseCase): SuspendPatronRoute =
    SuspendPatronRoute(useCase)
}
