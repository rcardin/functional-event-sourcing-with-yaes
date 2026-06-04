package in.rcard.fes.copy.adapter

import in.rcard.fes.copy.adapter.Routes.ProblemDetailsDTO.ErrorDTO
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import io.circe.Encoder

object Routes {

  // The class is a super simplified version of the RFC 9457 Problem Details for HTTP APIs.
  case class ProblemDetailsDTO(
      title: String,
      detail: String,
      errors: Seq[ErrorDTO]
  ) derives Encoder.AsObject
  object ProblemDetailsDTO {
    case class ErrorDTO(
        detail: String
    ) derives Encoder.AsObject
  }
}
