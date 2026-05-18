package in.rcard.fes.utils

import in.rcard.yaes.Log
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LogSpecVerification extends AnyFlatSpec with LogSpec with Matchers {

  "LogSpec" should "provide a Log that does not throw on any log method" in {
    val logger = Log.getLogger("test")
    noException should be thrownBy {
      logger.trace("trace msg")
      logger.debug("debug msg")
      logger.info("info msg")
      logger.warn("warn msg")
      logger.error("error msg")
      logger.fatal("fatal msg")
    }
  }
}
