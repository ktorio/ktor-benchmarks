package benchmarks

import benchmarks.utils.SAVE_REPORT
import benchmarks.utils.measureAllocations
import benchmarks.utils.startServer
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import utils.benchmarks.*
import kotlin.math.absoluteValue

const val TEST_SIZE = 300L
const val TRACKED_WARMUP_SIZE = 50
const val KB = 1024L

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
            cleanup = { server.stop(1000, 5000) },
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
        val tolerance = loadAllocationTolerance(reportName)
        val previousSnapshot = if (SAVE_REPORT) null else loadReport(reportName)
        val attempts = collectAllocationAttempts(
            measure = { runBlocking { measureEngineRequest(engine, requestCount, block) } },
            shouldRetry = { current ->
                SAVE_REPORT || compareAllocations(
                    previous = requireNotNull(previousSnapshot),
                    current = current,
                    tolerance = tolerance,
                ).exceedsDefaultTolerance
            },
        )
        val snapshot = selectLowestAllocationAttempt(attempts)
        val attemptSizes = attempts.map(AllocationData::totalSize)
        val attemptSpread = attemptSizes.max() - attemptSizes.min()

        if (attempts.size > 1) {
            val selectedAttempt = attempts.indexOf(snapshot)
            println("Allocation attempts for $reportName:")
            attemptSizes.forEachIndexed { index, size ->
                val selected = if (index == selectedAttempt) " (selected)" else ""
                println("  ${index}: ${size.kb}$selected")
            }
            println("  Spread: $attemptSpread bytes")
        }

        saveReport(reportName, snapshot, replace = SAVE_REPORT)
        saveSiteStatistics(reportName, snapshot, replace = SAVE_REPORT)

        if (SAVE_REPORT) {
            println("Report updated: $reportName")
            return
        }

        val baseline = requireNotNull(previousSnapshot)
        val consumedMemory = snapshot.totalSize()
        val expectedMemory = baseline.totalSize()
        val comparison = compareAllocations(
            previous = baseline,
            current = snapshot,
            tolerance = tolerance,
        )

        val difference = comparison.totalDifference
        val success = comparison.unexpectedIncrease == 0L
        val message = buildString {
            appendLine("Request consumes ${consumedMemory.kb}, expected ${expectedMemory.kb}.")
            if (attempts.size > 1) {
                appendLine("  Selected lowest of ${attempts.size} attempts; spread: $attemptSpread bytes.")
            }
            if (comparison.consumedKnownVariance == 0L) {
                appendLine(
                    "  Difference: ${difference.withSign} " +
                            "${if (success) "<=" else ">"} ${comparison.allowedDifference} (allowed)"
                )
            } else {
                appendLine("  Raw difference: ${difference.withSign}")
                appendLine(
                    "  Difference after known variance: ${comparison.adjustedDifference.withSign} " +
                            "${if (success) "<=" else ">"} ${comparison.allowedDifference} (allowed)"
                )
            }
            appendLine(
                "  ${if (difference > 0L) "Extra" else "Saved"} " +
                        "${difference.absoluteValue} bytes on request"
            )
            append("(See stdout + build/allocations/* files for details)")
        }.also(::println)

        val diffs = comparison.locationDifferences
        val consumedVariances = comparison.consumedLocationVariances.associateBy { it.locationName }

        println("\nIncreased locations:")
        diffs.filter { it.difference > 0 }
            .sortedByDescending { it.difference }.forEach { diff ->
                val variance = consumedVariances[diff.locationName]
                val varianceDescription = variance
                    ?.let { "  [known variance up to ${it.configuredVariance.knownVarianceBytes} bytes]" }
                    .orEmpty()
                println(
                    "\t" +
                            diff.locationName.padEnd(40) +
                            diff.difference.withSign.padStart(10) +
                            "    (${diff.previousSize.padEnd(12)} --> ${diff.currentSize.padStart(12)})" +
                            varianceDescription
                )
                if (variance != null) {
                    println("\t    ${variance.configuredVariance.reason}")
                    variance.configuredVariance.reference?.let { println("\t    See: $it") }
                }
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

        assertEquals(0L, comparison.unexpectedIncrease, message)
    }

    protected abstract suspend fun measureEngineRequest(
        engine: String,
        requestCount: Long = TEST_SIZE,
        block: suspend () -> Unit,
    ): AllocationData

    private val Long.kb get() = "%.2f KB".format(toDouble() / KB.toDouble())
    private val Long.withSign get() = if (this > 0L) "+$this" else toString()
    private fun Long.padStart(padding: Int) = toString().padStart(padding)
    private fun Long.padEnd(padding: Int) = toString().padEnd(padding)
}
