package in.rcard.fes

import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.application.RegisterCopyRoute
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.yaes.Reader.read
import in.rcard.yaes.http.client.{Uri, YaesClient}
import in.rcard.yaes.http.server.{ServerDef, YaesServer}
import in.rcard.yaes.slf4j.Slf4jLog
import in.rcard.yaes.Raise.*
import in.rcard.yaes.{Clock, Input, Log, Output, Raise, Random, Reader, Resource, Shutdown, Sync, System, YaesApp}
import pureconfig.*

class App extends YaesApp {

  override def run: (Sync, Output, Input, Random, Clock, System) ?=> Unit = {
    Slf4jLog.run {

      val logger = Log.getLogger("YaesApp")

//      Raise.fold(ConfigSource.default.load[AppConfig].value) { error =>
////        logger.error(s"Failed to load configuration: ${error.prettyPrint()}")
//      } { appConfig =>
//
//        Shutdown.run {
//          Resource.run {
//            server(appConfig)
//              .run(port = appConfig)
//          }
//        }
//      }
    }
  }

  private def server()(using Random): ServerDef = {
    YaesServer.route(
      read[RegisterCopyRoute].registerCopyRoute
    )
  }

}
