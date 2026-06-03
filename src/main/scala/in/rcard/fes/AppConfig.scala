package in.rcard.fes

import in.rcard.fes.AppConfig.IsbnClientConfig
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.pureconfig.given
import _root_.pureconfig.*
import in.rcard.yaes.http.client.Uri
import in.rcard.yaes.Raise
import _root_.pureconfig.error.CannotConvert
import cats.data.Validated.Valid

case class AppConfig(
    port: Int,
    isbnClient: IsbnClientConfig,
    db: AppConfig.DbConfig
) derives ConfigReader
object AppConfig {
  // FIXME Check with a proper test
  case class IsbnClientConfig(host: Uri, apiKey: String :| Not[Empty]) derives ConfigReader

  case class DbConfig(
      host: String,
      port: Int,
      database: String,
      user: String,
      password: String
  ) derives ConfigReader

  given uriReader: ConfigReader[Uri] = ConfigReader.fromCursor[Uri] { cur =>
    cur.asString.flatMap { str =>
      Raise.fold(Uri(str)) { error =>
        cur.failed(CannotConvert(str, "Uri", s"Invalid URI"))
      } { uri =>
        Right(uri)
      }
    }
  }
}
