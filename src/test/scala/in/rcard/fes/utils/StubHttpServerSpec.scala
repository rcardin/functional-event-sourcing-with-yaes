package in.rcard.fes.utils

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait StubHttpServerSpec extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  val stubServer: StubHttpServer = new StubHttpServer()
  def stubBaseUrl: String        = stubServer.baseUrl

  abstract override def afterAll(): Unit =
    try stubServer.stop()
    finally super.afterAll()

  abstract override def beforeEach(): Unit =
    stubServer.reset()
    super.beforeEach()
}
