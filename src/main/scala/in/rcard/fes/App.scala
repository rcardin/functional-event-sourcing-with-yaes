package in.rcard.fes

import in.rcard.fes.copy.application.RegisterCopyRoute
import in.rcard.yaes.Reader.read
import in.rcard.yaes.http.server.{ServerDef, YaesServer}
import in.rcard.yaes.slf4j.Slf4jLog
import in.rcard.yaes.{Clock, Input, Output, Random, Shutdown, Sync, System, YaesApp}

class App extends YaesApp {

  override def run: (Sync, Output, Input, Random, Clock, System) ?=> Unit = {
    Slf4jLog.run {
      Shutdown.run {
        server().run(port = 8080)
      }
    }
  }

  private def server()(using Random): ServerDef = {
    YaesServer.route(
      read[RegisterCopyRoute].registerCopyRoute
    )
  }
}
