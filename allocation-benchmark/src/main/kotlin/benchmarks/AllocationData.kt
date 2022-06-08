package benchmarks

import kotlinx.serialization.Serializable

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

    fun totalSize(): Long = packages.map { it.locationSize }.sum()
}

/**
 * Returns how much extra bytes is allocated.
 */
fun allocationDiff(
    expected: AllocationData,
    actual: AllocationData
): Long {
    val expectedSize = expected.totalSize()
    val actualSize = actual.totalSize()

    return actualSize - expectedSize
}
