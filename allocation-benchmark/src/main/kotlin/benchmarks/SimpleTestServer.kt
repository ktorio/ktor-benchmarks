package benchmarks

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * A simple HTTP server implementation for testing client allocations.
 * This avoids using Ktor for the server to prevent server-related frames
 * from appearing in the allocation tracker stacktraces.
 */
class SimpleTestServer(port: Int) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val executor = Executors.newFixedThreadPool(10)

    init {
        server.executor = executor

        // Set up context for a simple text response
        server.createContext("/hello") { exchange ->
            val response = "Hello, World!"
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
            exchange.sendTextResponse(200, response)
        }

        // Set up context for static file serving
        server.createContext("/") { exchange ->
            val requestedFile = exchange.requestURI.path.trimStart('/').ifEmpty { "index.html" }
            val resourcePath = SimpleTestServer::class.java.classLoader.getResource("benchmarks/$requestedFile")

            println(resourcePath)
            if (resourcePath != null) {
                println(resourcePath.readText().take(100))
                exchange.responseHeaders.add("Content-Type", getContentType(resourcePath.file))
                exchange.sendTextResponse(200, resourcePath.readText())
            } else {
                exchange.sendTextResponse(404, "404 Not Found")
            }
        }
    }

    private fun HttpExchange.sendTextResponse(code: Int, response: String) {
        sendResponseHeaders(code, response.length.toLong())
        responseBody.use { outputStream -> outputStream.write(response.toByteArray()) }
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(1)
        executor.shutdown()
    }

    private fun getContentType(filePath: String) = when (filePath.substringAfterLast('.')) {
        "html" -> "text/html"
        else -> error("Unexpected file extension: $filePath")
    }
}
