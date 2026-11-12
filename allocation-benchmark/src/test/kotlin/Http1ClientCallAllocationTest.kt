package benchmarks

import benchmarks.utils.client
import benchmarks.utils.measureAllocations
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class ClientCallAllocationTest : BaseAllocationTest() {

    // Should be used only inside a runAllocationTest block
    private lateinit var client: HttpClient

    @ParameterizedTest
    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
    fun helloWorldFootprint(engine: String) {
        runAllocationTest(engine, "client/helloWorld[$engine]") {
            val response = client.get("/benchmarks/hello")
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsText()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
    fun fileResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/fileResponse[$engine]") {
            val response = client.get("/index.html")
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsText()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
    fun streamingResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/streamingResponse[$engine]") {
            client.prepareGet("/benchmarks/bytes?size=$TEST_FILE_SIZE").execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                response.bodyAsBytes()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
    fun gzipResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/gzipResponse[$engine]") {
            val response = client.get("/benchmarks/gzip?size=$TEST_FILE_SIZE")
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsBytes()
        }
    }

    override suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long,
        block: suspend () -> Unit
    ): AllocationData {
        return AllocationTracker.measureAllocations(
            count = requestCount,
            prepare = { client = client(engine) },
            cleanup = { client.close() },
            block = { block() },
        )
    }
}

private const val MB = 1024 * 1024
private const val TEST_FILE_SIZE = 2 * MB
