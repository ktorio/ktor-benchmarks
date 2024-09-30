package benchmarks

import benchmarks.utils.SAVE_REPORT
import benchmarks.utils.measureMemory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import utils.benchmarks.loadReport
import utils.benchmarks.normalizeReport
import utils.benchmarks.saveReport
import utils.benchmarks.saveSiteStatistics
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

const val TEST_SIZE = 300L
const val WARMUP_SIZE = 20

// TODO investigate why TC has higher memory usage.
const val ALLOWED_MEMORY_DIFFERENCE_RATIO = 0.12

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun helloWorldFootprint(engine: String) {
        runTest(
            engine,
            "helloWorld[$engine]",
            endpoint = "/hello"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Jetty", "Tomcat", "Netty", "CIO"])
    fun fileResponseFootprint(engine: String) {
        runTest(
            engine,
            "fileResponse[$engine]",
            endpoint = "/"
        )
    }

   private fun runTest(
        engine: String,
        reportName: String,
        endpoint: String,
        requestCount: Long = TEST_SIZE
    ) {
        val request = createRequest(endpoint)
        val snapshot = measureMemory(engine, requestCount) {
            makeRequest(request)
        }.let {
            normalizeReport(it, requestCount)
        }

        saveReport(reportName, snapshot, replace = SAVE_REPORT)
        saveSiteStatistics(reportName, snapshot, replace = SAVE_REPORT)

        val previousSnapshot = loadReport(reportName)
        val consumedMemory = snapshot.totalSize() / requestCount
        val expectedMemory = previousSnapshot.totalSize() / requestCount

        val difference = consumedMemory - expectedMemory

        val allowedDifference = (ALLOWED_MEMORY_DIFFERENCE_RATIO * expectedMemory).roundToLong()
        val increase = maxOf(difference - allowedDifference, 0)
        val success = increase == 0L
        val message = """
            Request consumes $consumedMemory bytes, expected $expectedMemory. Difference: $difference ${if (success) "<" else ">"} $allowedDifference (allowed)
              Consumed $consumedMemory bytes on request
              Expected $expectedMemory bytes on request
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

    data class LocationDifference(
        val previous: LocationInfo?,
        val current: LocationInfo
    ) {
        fun difference() = previous?.let { current.locationSize - it.locationSize } ?: current.locationSize
    }

    private fun Long.padStart(padding: Int) = toString().padStart(padding)
    private fun Long.padEnd(padding: Int) = toString().padEnd(padding)

}
