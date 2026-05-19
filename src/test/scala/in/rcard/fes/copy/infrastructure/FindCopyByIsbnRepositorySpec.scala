package in.rcard.fes.copy.infrastructure

import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Domain.Author
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.Domain.Title
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.utils.LogSpec
import in.rcard.fes.utils.RaiseSpec
import in.rcard.fes.utils.StubHttpServerSpec
import in.rcard.fes.utils.StubResponse
import in.rcard.fes.utils.SyncSpec
import in.rcard.yaes.Raise
import in.rcard.yaes.Resource
import in.rcard.yaes.http.client.Uri
import in.rcard.yaes.http.client.YaesClient
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val API_KEY: String :| Not[Empty] = "test-api-key"

private val VALID_BOOK_JSON =
  """{
      |  "title": "Foundation",
      |  "isbn13": "9780593135204",
      |  "isbn10": "0593135202",
      |  "deweyDecimal": [],
      |  "binding": "Paperback",
      |  "publisher": "Spectra",
      |  "language": "en",
      |  "datePublished": "2020-07-07",
      |  "edition": "Reissue",
      |  "pages": 255,
      |  "dimensionsStructured": [],
      |  "image": "",
      |  "imageOriginal": "",
      |  "msrp": 0.0,
      |  "excerpt": "",
      |  "synopsis": "",
      |  "authors": ["Isaac Asimov"],
      |  "subjects": [],
      |  "prices": [],
      |  "otherIsbns": []
      |}""".stripMargin

class FindCopyByIsbnRepositorySpec
    extends AnyFlatSpec
    with StubHttpServerSpec
    with LogSpec
    with SyncSpec
    with RaiseSpec
    with Matchers {

  private def clientConfig: IsbnClientConfig = {
    val host = failOnRaise(Uri(stubBaseUrl))
    IsbnClientConfig(host, API_KEY)
  }
  val underTest: Resource ?=> FindCopyByIsbnPort =
    FindCopyByIsbnRepository(YaesClient.make(), clientConfig)

  "FindCopyByIsbnRepository" should "return correct CopyToRegister and send correct request on HTTP 200" in withSync {
    Resource.run {
      stubServer.setHandler(_ => StubResponse(200, VALID_BOOK_JSON))

      val actualResult = failOnRaise(underTest.find(FOUNDATION_ISBN))

      actualResult shouldBe FindCopyByIsbnPort.CopyToRegister(
        FOUNDATION_ISBN,
        Title("Foundation"),
        Seq(Author("Isaac Asimov"))
      )

      val captured = stubServer.capturedRequests
      captured should have size 1
      captured.head.path shouldBe s"/books/${FOUNDATION_ISBN.value}"
      captured.head.headers should contain key "authorization"
      captured.head.headers("authorization") shouldBe List(API_KEY)
      captured.head.rawQuery shouldBe Some("with_prices=false")
    }
  }

  it should "raise NotFound error on HTTP 404" in withSync {
    Resource.run {
      stubServer.setHandler(_ => StubResponse(404, ""))

      val error = interceptRaised(underTest.find(FOUNDATION_ISBN))

      error shouldBe FindCopyByIsbnPort.Error.NotFound(FOUNDATION_ISBN)
    }
  }

  it should "raise UnexpectedError on HTTP 500" in withSync {
    Resource.run {
      stubServer.setHandler(_ => StubResponse(500, "Internal Server Error"))

      val error = interceptRaised(underTest.find(FOUNDATION_ISBN))

      error shouldBe a[FindCopyByIsbnPort.Error.UnexpectedError]
    }
  }

  it should "raise UnexpectedError on connection refused" in withSync {
    val unusedPort = {
      val s = new java.net.ServerSocket(0)
      val p = s.getLocalPort
      s.close()
      p
    }

    val refusedConfig = {
      val host = Raise.fold(Uri(s"http://localhost:$unusedPort")) { e =>
        throw new AssertionError(s"Invalid URI: $e")
      }(identity)
      IsbnClientConfig(host, API_KEY)
    }

    val error =
      Resource.run {
        val client = YaesClient.make()
        val repo   = FindCopyByIsbnRepository(client, refusedConfig)
        interceptRaised(repo.find(FOUNDATION_ISBN))
      }

    error shouldBe a[FindCopyByIsbnPort.Error.UnexpectedError]
  }

  it should "raise UnexpectedError on HTTP 200 with syntactically invalid JSON" in withSync {
    Resource.run {
      stubServer.setHandler(_ => StubResponse(200, """{ invalid json """))

      val error = interceptRaised(underTest.find(FOUNDATION_ISBN))

      error shouldBe a[FindCopyByIsbnPort.Error.UnexpectedError]
    }
  }

  it should "raise UnexpectedError on HTTP 200 with structurally invalid JSON" in withSync {
    Resource.run {
      stubServer.setHandler(_ => StubResponse(200, """{}"""))

      val error = interceptRaised(underTest.find(FOUNDATION_ISBN))

      error shouldBe a[FindCopyByIsbnPort.Error.UnexpectedError]
    }
  }
}
