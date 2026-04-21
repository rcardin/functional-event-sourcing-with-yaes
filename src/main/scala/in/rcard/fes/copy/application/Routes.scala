package in.rcard.fes.copy.application

import in.rcard.fes.copy.application.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.Reader
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import io.circe.{Decoder, Encoder}

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

  val live: Reader[RegisterCopyUseCase] ?=> Route[NoParams, NoQueryParams] =
    RegisterCopyRoute.live.registerCopyRoute
}
