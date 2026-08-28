package benchmarks

import benchmarks.utils.measureAllocations
import benchmarks.utils.startServer
import io.ktor.server.engine.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ServerCallAllocationTest : BaseAllocationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun helloWorldFootprint(engine: String) {
        runAllocationTest(
            engine,
            "helloWorld[$engine]",
            block = endpoint("/hello"),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun fileResponseFootprint(engine: String) {
        runAllocationTest(
            engine,
            "fileResponse[$engine]",
            block = endpoint("/"),
        )
    }

    private fun endpoint(path: String): () -> Unit {
        val request = createRequest(path)
        return { makeRequest(request) }
    }

    override suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long,
        block: suspend () -> Unit
    ): AllocationData {
        lateinit var server: EmbeddedServer<*, *>

        return AllocationTracker.measureAllocations(
            count = requestCount,
            prepare = { server = startServer(engine) },
            cleanup = { server.stop(1000, 5000) },
            block = { block() },
        )
    }
}
