package com.example

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InstanceData(val name: String) {
    private val _totalCount = AtomicLong()
    private val _totalSize = AtomicLong()

    val totalCount get() = _totalCount.get()
    val totalSize get() = _totalSize.get()

    fun add(size: Long) {
        _totalSize.addAndGet(size)
        _totalCount.incrementAndGet()
    }

    override fun toString(): String = "$name $totalCount ${totalSize.formatSize()}"
}

private class PackageData(val name: String) {
    private val _size = AtomicLong()
    val size get() = _size.get()

    val instanceIndex = ConcurrentHashMap<String, InstanceData>()

    fun add(instanceClass: Class<*>, size: Long, arraySize: Int) {
        _size.addAndGet(size)

        val instance = instanceIndex.computeIfAbsent(instanceClass.name) { InstanceData(instanceClass.name) }
        instance.add(size)
    }

    override fun toString(): String = buildString {
        val instances = instanceIndex.values.sortedByDescending { it.totalSize }

        appendLine("Package: $name. Size: ${size.formatSize()}")

        instances.forEach {
            appendLine("  $it")
        }
    }
}

fun Long.formatSize(): String = when {
    this >= 1024 * 1024 -> "${(this / 1024.0 / 1024.0)}Mb"
    this >= 1024 -> "${(this / 1024.0)}Kb"
    else -> "${this}b"
}

object AllocationSampler : Sampler {
    private val data = ConcurrentHashMap<String, PackageData>()

    fun startSampling() {
        AllocationRecorder.addSampler(this)
    }

    fun clear() {
        data.clear()
    }

    fun stats(): String = buildString {
        val packages = data.values.sortedByDescending { it.size }

        packages.forEach {
            appendLine(it)
        }
    }

    override fun sampleAllocation(count: Int, descriptor: String, instance: Any, size: Long) {
        val type = instance.javaClass
        val location = type.packageName
        val packageData = data.computeIfAbsent(location) { PackageData(location) }
        packageData.add(type, size, count)


//        println("I just allocated the object of type $type with desc $descriptor whose size is $size")
//        if (count != -1) {
//            println("It's an array of size $count")
//        }
    }
}