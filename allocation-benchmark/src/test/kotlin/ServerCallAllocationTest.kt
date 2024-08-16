package benchmarks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.absoluteValue

const val TEST_SIZE = 1000
const val WARMUP_SIZE = 10

// TODO investigate why TC has higher memory usage.
const val ALLOWED_MEMORY_DIFFERENCE_NORMAL = 1500L
const val ALLOWED_MEMORY_DIFFERENCE_ABNORMAL = 7500L

class ServerCallAllocationTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "Jetty",
        "Tomcat",
        "Netty",
        "CIO",
    ])
    fun testMemoryConsumptionIsSame(engine: String) {
        val reportName = "testMemoryConsumptionIsSame[$engine]"

        val snapshot = measureMemory(engine) {
            repeat(TEST_SIZE) {
                makeRequest()
            }
        }

        saveReport(reportName, snapshot, replace = SAVE_REPORT)
        saveSiteStatistics(reportName, snapshot, replace = SAVE_REPORT)

        val previousSnapshot = loadReport(reportName)
        val consumedMemory = snapshot.totalSize() / TEST_SIZE
        val expectedMemory = previousSnapshot.totalSize() / TEST_SIZE

        val difference = consumedMemory - expectedMemory

        // depending on the environment, the engine, and the cycle of the moon,
        // the memory consumption will change
        val allowedDifference =
            when(engine) {
                "CIO", "Tomcat" -> ALLOWED_MEMORY_DIFFERENCE_ABNORMAL
                else -> ALLOWED_MEMORY_DIFFERENCE_NORMAL
            }

        val message = """
            Request consumes ${consumedMemory.kb}, expected ${expectedMemory.kb}. Difference: ${difference.kb} > ${allowedDifference.kb} (allowed)
              Consumed ${consumedMemory.kb} on request
              Expected ${expectedMemory.kb} on request
              ${if (difference > 0L) "Extra   " else "Saved   "} ${difference.absoluteValue.kb} on request
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
            println("\t" + current.name.padEnd(40) + diff.difference().kb.padStart(10) + "    (${(previous?.locationSize?.kb ?: "0").padEnd(12)} --> ${current.locationSize.kb.padStart(12)})")
        }

        val increase = maxOf(difference - allowedDifference, 0)
        assertEquals(0L, increase, message)
    }

    private val Long.kb get() = "%.2f KB".format(toDouble() / 1024.0)

    data class LocationDifference(
        val previous: LocationInfo?,
        val current: LocationInfo
    ) {
        fun difference() = previous?.let { current.locationSize - it.locationSize } ?: current.locationSize
    }

}
