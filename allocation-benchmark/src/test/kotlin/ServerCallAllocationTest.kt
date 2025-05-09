package benchmarks

import benchmarks.utils.SAVE_REPORT
import benchmarks.utils.measureServerMemory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import utils.benchmarks.loadReport
import utils.benchmarks.saveReport
import utils.benchmarks.saveSiteStatistics
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

const val TEST_SIZE = 300L
const val WARMUP_SIZE = 20
const val KB = 1024L

// TODO investigate why TC has higher memory usage.
const val ALLOWED_MEMORY_DIFFERENCE_RATIO = 0.12

class ServerCallAllocationTest : BaseAllocationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun helloWorldFootprint(engine: String) {
        runAllocationTest(
            engine,
            "helloWorld[$engine]",
            endpoint = "/hello"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun fileResponseFootprint(engine: String) {
        runAllocationTest(
            engine,
            "fileResponse[$engine]",
            endpoint = "/"
        )
    }

    override fun measureEngineRequest(engine: String, endpoint: String, requestCount: Long): AllocationData {
        val request = createRequest(endpoint)
        return measureServerMemory(engine, requestCount) {
            makeRequest(request)
        }
    }
}

abstract class BaseAllocationTest {
    /**
     * Runs a test to measure memory allocations for a specific engine.
     *
     * @param engine The engine name to test
     * @param reportName The name for the report
     * @param endpoint The endpoint to use in the test
     * @param requestCount The number of requests to make
     */
    protected fun runAllocationTest(
        engine: String,
        reportName: String,
        endpoint: String,
        requestCount: Long = TEST_SIZE,
    ) {
        val snapshot = measureEngineRequest(engine, endpoint, requestCount)

        saveReport(reportName, snapshot, replace = SAVE_REPORT)
        saveSiteStatistics(reportName, snapshot, replace = SAVE_REPORT)

        val previousSnapshot = loadReport(reportName)
        val consumedMemory = snapshot.totalSize()
        val expectedMemory = previousSnapshot.totalSize()

        val difference = consumedMemory - expectedMemory

        val allowedDifference = (ALLOWED_MEMORY_DIFFERENCE_RATIO * expectedMemory).roundToLong()
        val increase = maxOf(difference - allowedDifference, 0)
        val success = increase == 0L
        val message = """
            Request consumes ${consumedMemory.kb} bytes, expected ${expectedMemory.kb}. 
              Difference: $difference ${if (success) "<" else ">"} $allowedDifference (allowed)
              Consumed ${consumedMemory.kb} bytes on request
              Expected ${expectedMemory.kb} bytes on request
              ${if (difference > 0L) "Extra   " else "Saved   "} ${difference.absoluteValue.padEnd(3)} bytes on request
            (See stdout + build/allocations/* files for details)
        """.trimIndent().also(::println)

        if (SAVE_REPORT) {
            println("Report updated: $reportName")
            return
        }

        println("\nIncreased locations:")
        snapshot.packages.mapNotNull { location ->
            LocationDifference(previousSnapshot[location.name], location).takeIf {
                it.difference() > 0
            }
        }.sortedByDescending { it.difference() }.forEach { diff ->
            val (previous, current) = diff
            println(
                "\t" +
                current.name.padEnd(40) +
                diff.difference().toString().padStart(10) +
                "    (${(previous?.locationSize ?: 0).padEnd(12)} --> ${current.locationSize.padStart(12)})"
            )
        }

        assertEquals(0L, increase, message)
    }

    protected abstract fun measureEngineRequest(
        engine: String,
        endpoint: String,
        requestCount: Long = TEST_SIZE,
    ): AllocationData

    /**
     * Represents a difference between two location infos.
     */
    private data class LocationDifference(
        val previous: LocationInfo?,
        val current: LocationInfo
    ) {
        fun difference() = previous?.let { current.locationSize - it.locationSize } ?: current.locationSize
    }

    private val Long.kb get() = "%.2f KB".format(toDouble() / KB.toDouble())
    private fun Long.padStart(padding: Int) = toString().padStart(padding)
    private fun Long.padEnd(padding: Int) = toString().padEnd(padding)
}
