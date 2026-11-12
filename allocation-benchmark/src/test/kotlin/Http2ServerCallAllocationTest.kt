package benchmarks

import benchmarks.utils.measureAllocations
import benchmarks.utils.startHttp2Server
import io.ktor.server.engine.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Same endpoints as [Http1ServerCallAllocationTest], but served over HTTP/2 with TLS.
 * CIO is excluded, as it supports neither TLS nor HTTP/2 on the server side.
 */
class Http2ServerCallAllocationTest : BaseAllocationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty"])
    fun helloWorldFootprint(engine: String) {
        runAllocationTest(
            engine,
            "http2/helloWorld[$engine]",
            block = endpoint("/hello"),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty"])
    fun fileResponseFootprint(engine: String) {
        runAllocationTest(
            engine,
            "http2/fileResponse[$engine]",
            block = endpoint("/"),
        )
    }

    private fun endpoint(path: String): () -> Unit {
        val request = createHttp2Request(path)
        return { makeHttp2Request(request) }
    }

    override suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long,
        block: suspend () -> Unit
    ): AllocationData {
        lateinit var server: EmbeddedServer<*, *>

        return AllocationTracker.measureAllocations(
            count = requestCount,
            prepare = { server = startHttp2Server(engine) },
            cleanup = { server.stop(1000, 5000) },
            block = { block() },
        )
    }
}
