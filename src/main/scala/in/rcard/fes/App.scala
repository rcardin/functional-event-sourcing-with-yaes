package in.rcard.fes

import in.rcard.fes.copy.application.RegisterCopyRoute
import in.rcard.fes.copy.domain.usecase.{CopyIdGenerator, RegisterCopyUseCase}
import in.rcard.yaes.http.server.YaesServer
import in.rcard.yaes.slf4j.Slf4jLog
import in.rcard.yaes.{Reader, Shutdown, YaesApp}

class App extends YaesApp {

  override def run: Unit = {
    Slf4jLog.run {
      Shutdown.run {
        server().run(port = 8080)
      }
    }
  }

  private def server() = {
    Reader.run(CopyIdGenerator.live) {
      Reader.run(RegisterCopyUseCase.live) {
        YaesServer.route(
          RegisterCopyRoute.live.registerCopyRoute
        )
      }
    }
  }
}
