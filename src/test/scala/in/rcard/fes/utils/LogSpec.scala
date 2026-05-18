package in.rcard.fes.utils

import in.rcard.yaes.{Log, Logger}

trait LogSpec {
  given Log = new Log.Unsafe {
    override def getLogger(loggerName: String): Logger = new Logger {
      override val name: String                                = loggerName
      override def trace(msg: => String)(using Log): Unit     = ()
      override def debug(msg: => String)(using Log): Unit     = ()
      override def info(msg: => String)(using Log): Unit      = ()
      override def warn(msg: => String)(using Log): Unit      = ()
      override def error(msg: => String)(using Log): Unit     = ()
      override def fatal(msg: => String)(using Log): Unit     = ()
    }
  }
}
