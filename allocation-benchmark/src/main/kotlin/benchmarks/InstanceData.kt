package benchmarks

import kotlinx.serialization.Serializable
import kotlin.math.round

@Serializable
private class SiteStatistic(val lineNumber: Int) {
    private var totalCount: Long = 0L
    private var totalSize: Long = 0L

    fun add(size: Long) {
        totalCount += 1
        totalSize += size
    }

    override fun toString(): String = "Line: $lineNumber, Size: $totalSize, Count: $totalCount"
}

@Serializable
class InstanceData(val name: String) {
    private val sites = mutableMapOf<Int, SiteStatistic>()
    var totalSize: Long = 0L

    fun add(size: Long, lineNumber: Int) = synchronized(this) {
        totalSize += size

        val stats = sites.computeIfAbsent(lineNumber) { SiteStatistic(lineNumber) }
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