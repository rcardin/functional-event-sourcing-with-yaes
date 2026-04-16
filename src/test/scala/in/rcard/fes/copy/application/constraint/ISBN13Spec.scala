package in.rcard.fes.copy.application.constraint

import in.rcard.fes.copy.application.constraint.ISBN13
import io.github.iltotore.iron.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ISBN13Spec extends AnyFlatSpec with Matchers {

  "ISBN13 constraint" should "accept a valid ISBN-13 with 978 prefix and hyphens" in {
    "978-3-954-76392-4".refineEither[ISBN13] shouldBe Right("978-3-954-76392-4")
  }

  it should "accept a valid ISBN-13 with 978 prefix and no separators" in {
    "9780306406157".refineEither[ISBN13] shouldBe Right("9780306406157")
  }

  it should "accept a valid ISBN-13 with 979 prefix" in {
    "9791234567896".refineEither[ISBN13] shouldBe Right("9791234567896")
  }

  it should "accept a valid ISBN-13 with spaces as separators" in {
    "978 0 306 40615 7".refineEither[ISBN13] shouldBe Right("978 0 306 40615 7")
  }

  it should "reject an ISBN-13 with an invalid checksum" in {
    "9780306406158".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an ISBN-13 with fewer than 13 digits" in {
    "978030640615".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an ISBN-13 with more than 13 digits" in {
    "97803064061570".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an ISBN-13 with an invalid prefix" in {
    "9770306406157".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an ISBN-10" in {
    "0-306-40615-2".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject a string containing non-digit characters" in {
    "97803064061X7".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an empty string" in {
    "".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }

  it should "reject an ISBN-13 mixing hyphens and spaces" in {
    "978-0 306 40615-7".refineEither[ISBN13] shouldBe Left("Should be a valid ISBN-13")
  }
}
