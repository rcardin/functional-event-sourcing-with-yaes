package in.rcard.fes.utils

import in.rcard.yaes.Raise
import org.scalatest.exceptions.TestFailedException

import scala.reflect.ClassTag

trait RaiseSpec {

  def failOnRaise[E, A](body: Raise[E] ?=> A): A = {
    Raise.fold[E, A, A](block = body)(onError = error => {
      throw new TestFailedException(
        "Expected the test not to raise any errors but it did with error '$error'",
        3
      )
    })(onSuccess = identity)
  }

  def interceptRaised[E](body: Raise[E] ?=> Any)(using ErrorClass: ClassTag[E]): E = {
    val result: E | Any = Raise.run {
      body
    }
    result match
      case error: E => error
      case _        =>
        val expectedExceptionMsg =
          s"Expected error of type '${ErrorClass.runtimeClass.getName}' but body evaluated successfully"
        throw new TestFailedException(
          expectedExceptionMsg,
          0
        )
  }
}
