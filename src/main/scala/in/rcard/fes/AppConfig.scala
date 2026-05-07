package in.rcard.fes

import in.rcard.fes.AppConfig.IsbnClientConfig
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.pureconfig.given
import _root_.pureconfig.*

case class AppConfig(
    port: Int,
    isbnClient: IsbnClientConfig
) derives ConfigReader
object AppConfig {
  // FIXME Check with a proper test
  case class IsbnClientConfig(host: String :| ValidURL, apiKey: String) derives ConfigReader
}
