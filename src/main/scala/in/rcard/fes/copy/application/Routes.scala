package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.Reader
import in.rcard.yaes.http.server.params.path.NoParams
import in.rcard.yaes.http.server.params.query.NoQueryParams
import in.rcard.yaes.http.server.{POST, Response, p}
import in.rcard.yaes.http.server.routing.Route

object Routes {

  trait RegisterCopyRoute {
    val registerCopyRoute: Route[NoParams, NoQueryParams]
  }
  object RegisterCopyRoute {
    def apply()(using Reader[RegisterCopyUseCase]): RegisterCopyRoute = new RegisterCopyRoute {

      private val registerCopyUseCase = Reader.read

      val registerCopyRoute: Route[NoParams, NoQueryParams] = POST(p"/copies") { req =>
        Response.created("Ok")
      }
    }
  }
}
