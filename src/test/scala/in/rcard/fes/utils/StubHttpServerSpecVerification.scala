package in.rcard.fes.utils

import java.net.URI
import java.net.http.{HttpClient => JHttpClient, HttpRequest => JHttpRequest, HttpResponse => JHttpResponse}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StubHttpServerSpecVerification
    extends AnyFlatSpec
    with StubHttpServerSpec
    with Matchers {

  private val httpClient = JHttpClient.newBuilder().build()

  private def sendRequest(
      method: String,
      path: String,
      query: Option[String] = None,
      headers: Map[String, String] = Map.empty,
      body: String = ""
  ): (Int, String) = {
    val urlStr = query.fold(s"$stubBaseUrl$path")(q => s"$stubBaseUrl$path?$q")
    val reqBuilder = JHttpRequest
      .newBuilder(URI.create(urlStr))
      .method(
        method,
        if body.isEmpty then JHttpRequest.BodyPublishers.noBody()
        else JHttpRequest.BodyPublishers.ofString(body)
      )
    headers.foreach((k, v) => reqBuilder.header(k, v))
    val response = httpClient.send(reqBuilder.build(), JHttpResponse.BodyHandlers.ofString())
    (response.statusCode(), response.body())
  }

  "StubHttpServer" should "return 500 when no handler is configured" in {
    val (status, body) = sendRequest("GET", "/test")
    status shouldBe 500
    body shouldBe "no handler configured"
  }

  it should "return the response from the configured handler" in {
    stubServer.setHandler(_ => StubResponse(200, "hello"))
    val (status, body) = sendRequest("GET", "/test")
    status shouldBe 200
    body shouldBe "hello"
  }

  it should "capture method and path" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("POST", "/some/path")
    val captured = stubServer.capturedRequests
    captured should have size 1
    captured.head.method shouldBe "POST"
    captured.head.path shouldBe "/some/path"
  }

  it should "capture the raw query string" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("GET", "/books/123", query = Some("with_prices=false"))
    val captured = stubServer.capturedRequests
    captured.head.rawQuery shouldBe Some("with_prices=false")
  }

  it should "capture headers with lowercase keys" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("GET", "/test", headers = Map("Authorization" -> "Bearer token123"))
    val captured = stubServer.capturedRequests
    captured.head.headers should contain key "authorization"
    captured.head.headers("authorization") shouldBe "Bearer token123"
  }

  it should "capture the request body" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("POST", "/test", body = """{"isbn":"1234567890"}""")
    val captured = stubServer.capturedRequests
    captured.head.body shouldBe """{"isbn":"1234567890"}"""
  }

  it should "append multiple captured requests in order" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("GET", "/first")
    sendRequest("GET", "/second")
    val captured = stubServer.capturedRequests
    captured should have size 2
    captured(0).path shouldBe "/first"
    captured(1).path shouldBe "/second"
  }

  it should "clear captures and revert handler to default on reset()" in {
    stubServer.setHandler(_ => StubResponse(200, "ok"))
    sendRequest("GET", "/test")
    stubServer.capturedRequests should have size 1

    stubServer.reset()

    stubServer.capturedRequests shouldBe empty
    val (status, _) = sendRequest("GET", "/test")
    status shouldBe 500
  }
}
