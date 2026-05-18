package in.rcard.fes.utils

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

case class CapturedRequest(
    method: String,
    path: String,
    rawQuery: Option[String],
    headers: Map[String, String],
    body: String
)

case class StubResponse(
    statusCode: Int,
    body: String,
    headers: Map[String, String] = Map.empty
)

class StubHttpServer {
  private val defaultHandler: CapturedRequest => StubResponse =
    _ => StubResponse(500, "no handler configured")

  private val handlerRef    = new AtomicReference[CapturedRequest => StubResponse](defaultHandler)
  private val requestsQueue = new ConcurrentLinkedQueue[CapturedRequest]()

  private val jdkServer: HttpServer =
    val s = HttpServer.create(new InetSocketAddress(0), 0)
    s.createContext(
      "/",
      (exchange: HttpExchange) => {
        val bodyBytes = exchange.getRequestBody.readAllBytes()
        val body      = new String(bodyBytes, UTF_8)
        val uri       = exchange.getRequestURI
        val captured = CapturedRequest(
          method = exchange.getRequestMethod,
          path = uri.getRawPath,
          rawQuery = Option(uri.getRawQuery),
          headers = exchange.getRequestHeaders.entrySet().asScala.map { entry =>
            entry.getKey.toLowerCase(Locale.ROOT) -> entry.getValue.asScala.mkString(", ")
          }.toMap,
          body = body
        )
        requestsQueue.add(captured)
        val response      = handlerRef.get()(captured)
        val responseBytes = response.body.getBytes(UTF_8)
        response.headers.foreach { (k, v) => exchange.getResponseHeaders.set(k, v) }
        exchange.sendResponseHeaders(response.statusCode, responseBytes.length)
        val os = exchange.getResponseBody
        os.write(responseBytes)
        os.close()
      }
    )
    s.setExecutor(null)
    s.start()
    s

  val port: Int       = jdkServer.getAddress.getPort
  val baseUrl: String = s"http://localhost:$port"

  def setHandler(handler: CapturedRequest => StubResponse): Unit =
    handlerRef.set(handler)

  def capturedRequests: List[CapturedRequest] = requestsQueue.asScala.toList

  def reset(): Unit =
    requestsQueue.clear()
    handlerRef.set(defaultHandler)

  def stop(): Unit = jdkServer.stop(0)
}
