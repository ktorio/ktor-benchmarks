package benchmarks

import benchmarks.utils.measureClientMemory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ClientCallAllocationTest : BaseAllocationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
    fun helloWorldFootprint(engine: String) = runBlocking {
        runAllocationTest(
            engine,
            "clientHelloWorld[$engine]",
            endpoint = "/hello"
        )
    }

//    @ParameterizedTest
//    @ValueSource(strings = ["CIO", "Apache", "OkHttp", "Java"])
//    fun fileResponseFootprint(engine: String) = runBlocking {
//        runTest(
//            engine,
//            "clientFileResponse[$engine]",
//            endpoint = "/"
//        )
//    }

    override fun measureEngineRequest(engine: String, endpoint: String, requestCount: Long): AllocationData {
        return runBlocking {
            measureClientMemory(engine, requestCount, endpoint)
        }
    }
}
