package benchmarks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

const val TEST_SIZE = 1000
const val WARMUP_SIZE = 10

const val ALLOWED_MEMORY_DIFFERENCE = 100

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun testMemoryConsumptionIsSame(engine: String) {
        val reportName = "testMemoryConsumptionIsSame[$engine]"

        val memory = measureMemory(engine) {
            repeat(TEST_SIZE) {
                makeRequest()
            }
        }

        if (SAVE_REPORT) {
            saveReport(reportName, memory)
            saveSiteStatistics(reportName, memory)
            println("Report updated: $reportName")
            val consumedMemory = memory.totalSize() / TEST_SIZE
            println("Request consumes ${consumedMemory.kb}")
            return
        }

        val consumedMemory = memory.totalSize() / TEST_SIZE
        val expectedMemory = loadReport(reportName).totalSize() / TEST_SIZE

        val difference = consumedMemory - expectedMemory
        val message = """
            Request consumes ${consumedMemory.kb}, expected ${expectedMemory.kb}. Difference: ${(consumedMemory - expectedMemory).kb}
            Consumed ${consumedMemory.kb} on request
            Expected ${expectedMemory.kb} on request
            Extra consumed ${(consumedMemory - expectedMemory).kb} on request
        """.trimIndent().also(::println)

        assertTrue(difference - ALLOWED_MEMORY_DIFFERENCE <= 0, message)
    }

    private val Long.kb get() = "%.2f KB".format(toDouble() / 1024.0)

}
