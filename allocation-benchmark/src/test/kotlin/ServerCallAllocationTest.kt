package benchmarks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*
import kotlin.math.exp

const val TEST_SIZE = 1000
const val WARMUP_SIZE = 10

const val ALLOWED_MEMORY_DIFFERENCE = 100

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty"])
    fun testMemoryConsumptionIsSame(engine: String) {
        val reportName = "testMemoryConsumptionIsSame[$engine]"

        val memory = measureMemory(engine) {
            repeat(TEST_SIZE) {
                makeRequest()
            }
        }

        if (SAVE_REPORT) {
            saveReport(reportName, memory)
            println("Report updated: $reportName")
            return
        }

        val consumedMemory = memory.totalSize() / TEST_SIZE
        val expectedMemory = loadReport(reportName).totalSize() / TEST_SIZE

        println("Request consumes ${consumedMemory / 1024} KB, expected ${expectedMemory / 1024} KB. Difference: ${(consumedMemory - expectedMemory) / 1024} KB")
        println("Consumed ${consumedMemory / TEST_SIZE} Bytes on request")
        println("Expected ${expectedMemory / TEST_SIZE} Bytes on request")
        println("Extra consumed ${(consumedMemory - expectedMemory) / TEST_SIZE} Bytes on request")
        assertTrue(consumedMemory <= expectedMemory + ALLOWED_MEMORY_DIFFERENCE)
    }
}
