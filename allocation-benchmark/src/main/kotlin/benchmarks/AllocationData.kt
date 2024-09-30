package benchmarks

import kotlinx.serialization.Serializable

@Serializable
data class AllocationData(
    val data: MutableMap<String, LocationInfo> = mutableMapOf(),
) {
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