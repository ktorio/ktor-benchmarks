package benchmarks

import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*

const val TEST_SIZE = 100
const val WARMUP_SIZE = 10

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty"])
    fun testMemoryConsumptionIsSame(engine: String) {
        measureMemory("testMemoryConsumptionIsSame", engine) {
            repeat(TEST_SIZE) {
                makeRequest()
            }
        }
    }
}
