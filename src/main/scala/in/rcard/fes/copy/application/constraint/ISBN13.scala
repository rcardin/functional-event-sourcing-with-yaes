package in.rcard.fes.copy.application.constraint

import io.github.iltotore.iron.*

final class ISBN13

object ISBN13 {

  given Constraint[String, ISBN13] with
    override inline def test(inline value: String): Boolean = ISBN13.isValid(value)
    override inline def message: String                     = "Should be a valid ISBN-13"

  private def isValid(value: String): Boolean = {
    val hasHyphen = value.contains('-')
    val hasSpace  = value.contains(' ')
    if (hasHyphen && hasSpace) false
    else {
      val digits = value.filterNot(c => c == '-' || c == ' ')
      digits.length == 13 &&
      digits.forall(_.isDigit) &&
      (digits.startsWith("978") || digits.startsWith("979")) &&
      checksum(digits) == 0
    }
  }

  private def checksum(digits: String): Int = {
    val sum = digits.zipWithIndex.foldLeft(0) { case (acc, (c, i)) =>
      val d = c.asDigit
      acc + (if (i % 2 == 0) d else d * 3)
    }
    sum % 10
  }
}
