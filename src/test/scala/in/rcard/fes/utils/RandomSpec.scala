package in.rcard.fes.utils

import in.rcard.yaes.Random
import org.scalatest.{Outcome, TestSuite}
import org.scalatest.exceptions.TestFailedException

import scala.collection.mutable

class RandomStub extends Random.Unsafe {
  private val ints     = mutable.Queue[Int]()
  private val longs    = mutable.Queue[Long]()
  private val booleans = mutable.Queue[Boolean]()
  private val doubles  = mutable.Queue[Double]()

  def nextInts(values: Int*): Unit        = ints.enqueueAll(values)
  def nextLongs(values: Long*): Unit      = longs.enqueueAll(values)
  def nextBooleans(values: Boolean*): Unit = booleans.enqueueAll(values)
  def nextDoubles(values: Double*): Unit  = doubles.enqueueAll(values)

  def reset(): Unit = { ints.clear(); longs.clear(); booleans.clear(); doubles.clear() }

  override def nextInt(): Int =
    if ints.isEmpty then throw new TestFailedException("RandomStub: no ints queued", 0)
    else ints.dequeue()

  override def nextLong(): Long =
    if longs.isEmpty then throw new TestFailedException("RandomStub: no longs queued", 0)
    else longs.dequeue()

  override def nextBoolean(): Boolean =
    if booleans.isEmpty then throw new TestFailedException("RandomStub: no booleans queued", 0)
    else booleans.dequeue()

  override def nextDouble(): Double =
    if doubles.isEmpty then throw new TestFailedException("RandomStub: no doubles queued", 0)
    else doubles.dequeue()
}

trait RandomSpec extends TestSuite {
  val RandomStub: RandomStub = new RandomStub()
  given Random               = RandomStub

  abstract override def withFixture(test: NoArgTest): Outcome = {
    RandomStub.reset()
    super.withFixture(test)
  }
}
