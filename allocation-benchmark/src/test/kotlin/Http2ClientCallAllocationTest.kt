package benchmarks

import benchmarks.utils.http2Client
import benchmarks.utils.measureAllocations
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

/**
 * Same endpoints as [ClientCallAllocationTest], but requested over HTTP/2 with TLS.
 * CIO is excluded, as its client doesn't support HTTP/2.
 */
class Http2ClientCallAllocationTest : BaseAllocationTest() {

    // Should be used only inside a runAllocationTest block
    private lateinit var client: HttpClient

    @ParameterizedTest
    @ValueSource(strings = ["Apache", "OkHttp", "Java"])
    fun helloWorldFootprint(engine: String) {
        runAllocationTest(engine, "client/http2/helloWorld[$engine]") {
            val response = client.get("/benchmarks/hello")
            response.assertHttp2Ok()
            response.bodyAsText()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Apache", "OkHttp", "Java"])
    fun fileResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/http2/fileResponse[$engine]") {
            val response = client.get("/index.html")
            response.assertHttp2Ok()
            response.bodyAsText()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Apache", "OkHttp", "Java"])
    fun streamingResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/http2/streamingResponse[$engine]") {
            client.prepareGet("/benchmarks/bytes?size=$TEST_FILE_SIZE").execute { response ->
                response.assertHttp2Ok()
                response.bodyAsBytes()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Apache", "OkHttp", "Java"])
    fun gzipResponseFootprint(engine: String) {
        runAllocationTest(engine, "client/http2/gzipResponse[$engine]") {
            val response = client.get("/benchmarks/gzip?size=$TEST_FILE_SIZE")
            response.assertHttp2Ok()
            response.bodyAsBytes()
        }
    }

    private fun HttpResponse.assertHttp2Ok() {
        assertEquals(HttpStatusCode.OK, status)
        assertEquals(HttpProtocolVersion.HTTP_2_0, version)
    }

    override suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long,
        block: suspend () -> Unit
    ): AllocationData {
        return AllocationTracker.measureAllocations(
            count = requestCount,
            prepare = { client = http2Client(engine) },
            cleanup = { client.close() },
            block = { block() },
        )
    }
}

private const val MB = 1024 * 1024
private const val TEST_FILE_SIZE = 2 * MB
