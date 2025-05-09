package benchmarks.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*

// See test-server.gradle.kts
private const val TEST_SERVER_PORT = 8081

/**
 * Creates a Ktor HTTP client with the specified engine.
 *
 * @param engine The engine name to use ("CIO", "Apache", "OkHttp", or "Java")
 * @return A configured HttpClient instance
 */
fun client(engine: String): HttpClient {
    val engineFactory: HttpClientEngineFactory<*> = when (engine.lowercase()) {
        "cio" -> CIO
        "apache" -> Apache
        "okhttp" -> OkHttp
        "java" -> Java
        else -> error("Unknown engine: $engine")
    }

    return HttpClient(engineFactory) {
        defaultRequest { url("http://127.0.0.1:$TEST_SERVER_PORT") }
    }
}
