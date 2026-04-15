package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.routing.Route
import in.rcard.yaes.http.server.{POST, Response, p}
import in.rcard.yaes.{Raise, Reader}
import io.circe.{Decoder, Encoder}

object Routes {

  trait RegisterCopyRoute {
    val registerCopyRoute: Route[NoParams, NoQueryParams]
  }
  object RegisterCopyRoute {

    case class RegisterCopyDTO(isbn: String, title: String, author: String)
        derives Encoder.AsObject,
          Decoder

    def apply()(using Reader[RegisterCopyUseCase]): RegisterCopyRoute = new RegisterCopyRoute {

      private val registerCopyUseCase = Reader.read

      val registerCopyRoute: Route[NoParams, NoQueryParams] = POST(p"/copies") { req =>
        Raise.recover {
          val dto = req.as[RegisterCopyDTO]
          Response.created[String]("Ok")
        } { error =>
          Response.badRequest("Ko")
        }
      }
    }
  }
}
