package benchmarks

import kotlinx.serialization.Serializable
import kotlin.math.round

@Serializable
private class SiteStatistic(val stackTrace: String) {
    private var totalCount: Long = 0L
    private var totalSize: Long = 0L

    fun add(size: Long) {
        totalCount += 1
        totalSize += size
    }

    override fun toString(): String = "Size: $totalSize, Count: $totalCount, Stack: $stackTrace"
}

@Serializable
class InstanceData(val name: String) {
    private val sites = mutableMapOf<String, SiteStatistic>()
    var totalSize: Long = 0L

    fun add(size: Long, stackTrace: List<String>) = synchronized(this) {
        totalSize += size

        val trace = stackTrace.joinToString()
        val stats = sites.computeIfAbsent(trace) { SiteStatistic(trace) }
        stats.add(size)
    }

    override fun toString(): String = buildString {
        appendLine(name)
        sites.values.forEach { value ->
            appendLine("  $value")
        }
    }
}

fun Long.formatSize(): String = when {
    this >= 1024 * 1024 -> "${clip(this / 1024.0 / 1024.0)} Mb"
    this >= 1024 -> "${clip(this / 1024.0)} Kb"
    else -> "$this b"
}

private fun clip(value: Double): Double = round(value * 100) / 100