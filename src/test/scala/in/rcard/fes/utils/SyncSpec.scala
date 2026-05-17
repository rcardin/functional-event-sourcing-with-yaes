package in.rcard.fes.utils

import in.rcard.yaes.Sync
import in.rcard.yaes.Executor
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

trait SyncSpec {
  private def runSync[A](program: Sync ?=> A): A = {
    val syncInstance = new Sync.Unsafe {
      override val executor: Executor = new Executor {
        override def submit[A](task: => A): Future[A] = Try {
          task
        }.fold(Future.failed(_), Future.successful)
      }
    }
    given Sync = syncInstance
    Await.result(syncInstance.executor.submit(program), Duration.Inf)
  }

  def withSync[A](program: Sync ?=> A): A = runSync(program)
}
