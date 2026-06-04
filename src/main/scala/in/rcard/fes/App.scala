package in.rcard.fes

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import in.rcard.fes.AppConfig.{DbConfig, IsbnClientConfig}
import in.rcard.fes.copy.adapter.RegisterCopyRoute
import in.rcard.fes.copy.application.CopyCommandHandler.live
import in.rcard.fes.copy.infrastructure.CopyPostgresEventStore.live
import in.rcard.fes.copy.infrastructure.FindCopyByIsbnRepository.live
import in.rcard.yaes.http.client.{Uri, YaesClient}
import in.rcard.yaes.http.server.{ServerDef, YaesServer}
import in.rcard.yaes.slf4j.Slf4jLog
import in.rcard.yaes.Raise.*
import in.rcard.yaes.{
  Clock,
  Input,
  Log,
  Output,
  Raise,
  Random,
  Resource,
  Shutdown,
  Sync,
  System,
  YaesApp
}
import org.flywaydb.core.Flyway
import pureconfig.*

class App extends YaesApp {

  override def run: (Sync, Output, Input, Random, Clock, System) ?=> Unit = {
    Slf4jLog.run {

      val logger = Log.getLogger("YaesApp")

      Raise.fold(ConfigSource.default.load[AppConfig].value) { error =>
        logger.error(s"Failed to load configuration: ${error.prettyPrint()}")
      } { appConfig =>
        Shutdown.run {
          Resource.run {
            server(appConfig)
              .run(port = appConfig.port)
          }
        }
      }
    }
  }

  private def server(appConfig: AppConfig)(using Log, Random, Resource, Sync): ServerDef = {

    given client: YaesClient                 = YaesClient.make()
    given isbnClientConfig: IsbnClientConfig = appConfig.isbnClient
    given pool: HikariDataSource             = makePool(appConfig.db)

    runMigrations(pool)

    YaesServer.route(
      summon[RegisterCopyRoute].registerCopyRoute
    )
  }

  private def makePool(dbConfig: DbConfig)(using Resource): HikariDataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl(s"jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.database}")
    config.setUsername(dbConfig.user)
    config.setPassword(dbConfig.password)
    Resource.acquire(new HikariDataSource(config))
  }

  private def runMigrations(pool: HikariDataSource): Unit = {
    // FIXME: Handle migration errors properly instead of just throwing exceptions
    Flyway.configure().dataSource(pool).load().migrate()
    ()
  }

}
