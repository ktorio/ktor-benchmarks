package benchmarks

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*

const val SERVER_PORT = 8080
const val TEST_SIZE = 100
const val WARMUP_SIZE = 10
val client = HttpClient(Apache) {
    developmentMode = false

    engine {
        socketTimeout = 0
        connectTimeout = 0
        connectionRequestTimeout = 0
    }
}

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun testMemoryConsumptionIsSame(engine: String): Unit = runBlocking {
        measureMemory("testMemoryConsumptionIsSame", engine) {
            repeat(TEST_SIZE) {
                client.get<String>("http://127.0.0.1:$SERVER_PORT")
            }
        }
    }
}