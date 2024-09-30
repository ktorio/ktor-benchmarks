package benchmarks

import kotlinx.serialization.Serializable

@Serializable
data class LocationInfo(
    val name: String,
    var locationSize: Long = 0L,
    val instanceIndex: MutableMap<String, InstanceData> = mutableMapOf()
) {
    val instances: MutableCollection<InstanceData> get() = instanceIndex.values

    fun add(instanceClass: Class<*>, size: Long, stackTrace: List<String>) = synchronized(this) {
        locationSize += size

        val instance = instanceIndex.computeIfAbsent(instanceClass.name) { InstanceData(instanceClass.name) }
        instance.add(size, stackTrace)
    }

    override fun toString(): String = buildString {
        appendLine("Location: $name. Size: ${locationSize.formatSize()}")
    }
}
