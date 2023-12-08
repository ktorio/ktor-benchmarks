package benchmarks

import kotlinx.serialization.Serializable

@Serializable
class LocationInfo(val name: String) {
    var locationSize = 0L
        private set

    private val instanceIndex = mutableMapOf<String, InstanceData>()

    public val instances: MutableCollection<InstanceData> get() = instanceIndex.values

    fun add(instanceClass: Class<*>, size: Long, stackTrace: List<String>) = synchronized(this) {
        locationSize += size

        val instance = instanceIndex.computeIfAbsent(instanceClass.name) { InstanceData(instanceClass.name) }
        instance.add(size, stackTrace)
    }

    override fun toString(): String = buildString {
        appendLine("Location: $name. Size: ${locationSize.formatSize()}")
    }
}
