package benchmarks

import benchmarks.utils.SAVE_REPORT
import benchmarks.utils.measureAllocations
import benchmarks.utils.startServer
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
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
            cleanup = { server.stop(1000, 1000) },
            block = { block() },
        )
    }
}

abstract class BaseAllocationTest {
    /**
     * Runs a test to measure memory allocations for a specific engine.
     *
     * @param engine The engine name to test
     * @param reportName The name for the report
     * @param requestCount The number of requests to make
     * @param block The block to execute for the test
     */
    protected fun runAllocationTest(
        engine: String,
        reportName: String,
        requestCount: Long = TEST_SIZE,
        block: suspend () -> Unit,
    ) {
        val snapshot = runBlocking {
            measureEngineRequest(engine, requestCount, block)
        }

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
            Request consumes ${consumedMemory.kb}, expected ${expectedMemory.kb}. 
              Difference: $difference ${if (success) "<" else ">"} $allowedDifference (allowed)
              Consumed ${consumedMemory.kb} on request
              Expected ${expectedMemory.kb} on request
              ${if (difference > 0L) "Extra   " else "Saved   "} ${difference.absoluteValue.padEnd(3)} bytes on request
            (See stdout + build/allocations/* files for details)
        """.trimIndent().also(::println)

        if (SAVE_REPORT) {
            println("Report updated: $reportName")
            return
        }

        val diffs = previousSnapshot diff snapshot

        println("\nIncreased locations:")
        diffs.filter { it.difference > 0 }
            .sortedByDescending { it.difference }.forEach { diff ->
                println(
                    "\t" +
                            diff.locationName.padEnd(40) +
                            diff.difference.toString().padStart(10) +
                            "    (${diff.previousSize.padEnd(12)} --> ${diff.currentSize.padStart(12)})"
                )
            }

        println("\nDecreased locations:")
        diffs.filter { it.difference < 0 }
            .sortedBy { it.difference }.forEach { diff ->
                println(
                    "\t" +
                            diff.locationName.padEnd(40) +
                            diff.difference.toString().padStart(10) +
                            "    (${diff.previousSize.padEnd(12)} --> ${diff.currentSize.padStart(12)})"
                )
            }

        assertEquals(0L, increase, message)
    }

    protected abstract suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long = TEST_SIZE,
        block: suspend () -> Unit,
    ): AllocationData

    private infix fun AllocationData.diff(other: AllocationData): List<LocationDifference> {
        val locations = this.packages.map { it.name }.toSet() + other.packages.map { it.name }.toSet()
        return locations.map { location ->
            LocationDifference(
                previous = this[location],
                current = other[location],
            )
        }
    }

    /**
     * Represents a difference between two location infos.
     */
    private class LocationDifference(
        val previous: LocationInfo?,
        val current: LocationInfo?,
    ) {
        val previousSize = previous?.locationSize ?: 0L
        val currentSize = current?.locationSize ?: 0L
        val locationName = current?.name ?: previous?.name ?: "unknown"
        val difference = currentSize - previousSize
    }

    private val Long.kb get() = "%.2f KB".format(toDouble() / KB.toDouble())
    private fun Long.padStart(padding: Int) = toString().padStart(padding)
    private fun Long.padEnd(padding: Int) = toString().padEnd(padding)
}
