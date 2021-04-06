package benchmarks

import kotlinx.serialization.Serializable

@Serializable
class LocationInfo(val name: String) {
    var locationSize = 0L
        private set

    private val instanceIndex = mutableMapOf<String, InstanceData>()

    fun add(instanceClass: Class<*>, size: Long, lineNumber: Int) = synchronized(this) {
        locationSize += size

        val instance = instanceIndex.computeIfAbsent(instanceClass.name) { InstanceData(instanceClass.name) }
        instance.add(size, lineNumber)
    }

    override fun toString(): String = buildString {
        val instances = instanceIndex.values.sortedByDescending { it.totalSize }

        appendLine("Location: $name. Size: ${locationSize.formatSize()}")
        instances.forEach {
            appendLine(it)
        }
    }
}