package in.rcard.fes.patron.adapter

import scala.language.implicitConversions
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO
import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.patron.application.{ReinstatePatronError, ReinstatePatronUseCase}
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

trait ReinstatePatronRoute {
  val reinstatePatronRoute: Sync ?=> Route[? <: PathParams, NoQueryParams]
}
object ReinstatePatronRoute {

  def apply(reinstatePatronUseCase: ReinstatePatronUseCase): ReinstatePatronRoute =
    new ReinstatePatronRoute {

      private def handlingDomainErrors(error: ReinstatePatronError): Response =
        error match
          case ReinstatePatronError.PatronNotFound(patronId) =>
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
          case ReinstatePatronError.NotSuspended(patronId) =>
            Response.withStatus(
              status = 409,
              value = ProblemDetailsDTO(
                title = "Conflict",
                detail = "Patron is not suspended.",
                errors = Seq(
                  ErrorDTO(
                    detail = s"The patron with card id '${patronId.value}' is not suspended and cannot be reinstated."
                  )
                )
              ),
              extraHeaders = Map(Headers.ContentType -> "application/json")
            )
          case ReinstatePatronError.UnexpectedError(_) =>
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

      override val reinstatePatronRoute: Sync ?=> Route[? <: PathParams, NoQueryParams] =
        POST(p"/patrons" / param[String]("cardId") / "reinstated") { (req, cardId: String) =>
          Raise.recover {
            reinstatePatronUseCase.reinstate(PatronId(cardId))
            Response.ok[String]("Ok")
          } { (error: ReinstatePatronError) => handlingDomainErrors(error) }
        }
    }

  given live(using useCase: ReinstatePatronUseCase): ReinstatePatronRoute =
    ReinstatePatronRoute(useCase)
}
