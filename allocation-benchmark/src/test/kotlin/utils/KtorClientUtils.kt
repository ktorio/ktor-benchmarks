package benchmarks.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import okhttp3.Protocol

// See test-server.gradle.kts
private const val TEST_SERVER_PORT = 8081
private const val HTTP2_TEST_SERVER_PORT = 8444

/**
 * Creates a Ktor HTTP client with the specified engine.
 *
 * @param engine The engine name to use ("CIO", "Apache", "OkHttp", or "Java")
 * @return A configured HttpClient instance
 */
fun client(engine: String): HttpClient {
    val engineFactory: HttpClientEngineFactory<*> = when (engine.lowercase()) {
        "cio" -> CIO
        "apache" -> Apache5
        "okhttp" -> OkHttp
        "java" -> Java
        else -> error("Unknown engine: $engine")
    }

    return HttpClient(engineFactory) {
        defaultRequest { url("http://127.0.0.1:$TEST_SERVER_PORT") }
    }
}

/**
 * Creates a Ktor HTTP client with the specified engine, talking HTTP/2 over TLS to the HTTP/2 test server.
 *
 * @param engine The engine name to use ("Apache", "OkHttp", or "Java"); CIO doesn't support HTTP/2
 * @return A configured HttpClient instance
 */
fun http2Client(engine: String): HttpClient {
    return when (engine.lowercase()) {
        "apache" -> HttpClient(Apache5) {
            http2Defaults()
            engine { sslContext = trustAllSslContext }
        }

        "okhttp" -> HttpClient(OkHttp) {
            http2Defaults()
            engine {
                config {
                    sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                }
            }
        }

        "java" -> HttpClient(Java) {
            http2Defaults()
            engine {
                protocolVersion = java.net.http.HttpClient.Version.HTTP_2
                config { sslContext(trustAllSslContext) }
            }
        }

        else -> error("HTTP/2 is not supported for engine: $engine")
    }
}

private fun HttpClientConfig<*>.http2Defaults() {
    defaultRequest { url("https://127.0.0.1:$HTTP2_TEST_SERVER_PORT") }
}
