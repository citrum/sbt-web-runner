package rosrabota.webrunner.server

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import scala.collection.mutable

abstract class SimpleHttpServerBase(val socketAddress: String = "127.0.0.1",
                                    val port: Int = 8080,
                                    val backlog: Int = 0) extends HttpHandler {
  private val address = new InetSocketAddress(socketAddress, port)
  private val server = HttpServer.create(address, backlog)
  server.setExecutor(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS, new SynchronousQueue[Runnable]))
  server.createContext("/", this)

  def respond(exchange: HttpExchange, code: Int = 200, body: String = "") {
    try {
      val bytes = body.getBytes
      exchange.sendResponseHeaders(code, bytes.size)
      exchange.getResponseBody.write(bytes)
      exchange.getResponseBody.write("\r\n\r\n".getBytes)
      exchange.getResponseBody.close()
      exchange.close()
    } catch {
      case e: IOException => // just ignore
    }
  }

  def start() {
    server.start()
  }

  def stop(delay: Int = 1) {
    server.stop(delay)
  }

  private val mappings = new mutable.HashMap[String, (HttpExchange) => Any]

  def get(path: String)(action: (HttpExchange) => Any) = mappings += path -> action

  def handle(exchange: HttpExchange) {
    mappings.get(exchange.getRequestURI.getPath) match {
      case None => respond(exchange, 404)
      case Some(action) => try {
        action(exchange)
      } catch {
        case ex: Exception => respond(exchange, 500, ex.toString)
      }
    }
  }
}
