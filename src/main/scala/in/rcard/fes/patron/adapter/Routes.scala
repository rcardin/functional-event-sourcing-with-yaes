package in.rcard.fes.patron.adapter

import in.rcard.fes.patron.adapter.Routes.ProblemDetailsDTO.ErrorDTO
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
