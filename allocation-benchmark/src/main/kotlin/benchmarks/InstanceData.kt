package benchmarks

import kotlinx.serialization.Serializable
import kotlin.math.round

@Serializable
class InstanceData(val name: String) {
    var totalCount = 0L
        private set
    var totalSize = 0L
        private set

    fun add(size: Long) = synchronized(this) {
        totalSize += (size)
        totalCount += 1
    }

    override fun toString(): String = "$name[Count: $totalCount, Size: ${totalSize.formatSize()}]"
}

fun Long.formatSize(): String = when {
    this >= 1024 * 1024 -> "${clip(this / 1024.0 / 1024.0)} Mb"
    this >= 1024 -> "${clip(this / 1024.0)} Kb"
    else -> "$this b"
}

private fun clip(value: Double): Double = round(value * 100) / 100