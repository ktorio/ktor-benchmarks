import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
class AllocationData {
    private val data = mutableMapOf<String, LocationInfo>()

    val packages: List<LocationInfo> get() = data.values.sortedByDescending { it.locationSize }

    fun clear() = synchronized(this) {
        data.clear()
    }

    fun add(name: String, block: (String) -> LocationInfo): LocationInfo = synchronized(this) {
        data.computeIfAbsent(name, block)
    }

    operator fun get(name: String): LocationInfo? = synchronized(this) {
        return@synchronized data[name]
    }
}

data class LocationDiff(val expected: LocationInfo? = null, val actual: LocationInfo? = null) {
    override fun toString(): String = when {
        expected == null -> buildString {
            appendLine("Found new allocation:")
            appendLine(actual)
        }
        actual == null -> buildString {
            appendLine("Allocation was removed:")
            appendLine(expected)
        }
        else -> buildString {
            appendLine("Difference in allocations:")
            appendLine("Expected:")
            appendLine(expected)
            appendLine("Actual:")
            appendLine(actual)
        }
    }
}

fun checkAllocationDataIsSame(
    expected: AllocationData,
    actual: AllocationData,
    allowedFileDifference: Long = 300 * 1024
) {
    val visited = mutableSetOf<String>()
    val problems = mutableListOf<LocationDiff>()
    actual.packages.forEach {
        visited.add(it.name)

        val expectedPackage = expected[it.name]
        if (expectedPackage == null) {
            if (it.locationSize > allowedFileDifference) {
                problems.add(LocationDiff(actual = it))
            }

            return@forEach
        }

        if (abs(expectedPackage.locationSize - it.locationSize) > allowedFileDifference) {
            problems.add(LocationDiff(expectedPackage, it))
        }
    }

    expected.packages.forEach {
        if (it.name in visited || it.locationSize <= allowedFileDifference) return@forEach

        problems.add(LocationDiff(expected = it))
    }

    if (problems.isEmpty()) return

    error(problems.joinToString("\n\n"))
}
