package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.yaes.test.scalatest.RandomSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CopyIdGeneratorSpec extends AnyFlatSpec with RandomSpec with Matchers {
  private val underTest = CopyIdGenerator()

  "CopyIdGenerator.generate" should "generate a CopyId from a deterministic UUID" in {
    rand.nextLongs(0x0123456789abcdefL, 0xfedcba9876543210L)
    underTest.generate() shouldBe CopyId("01234567-89ab-4def-bedc-ba9876543210")
  }
}
