package benchmarks

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler

object AllocationTracker : Sampler {
    private val walker = StackWalker.getInstance()
    private var started = false
    private val data = AllocationData()

    fun start() {
        if (started) {
            return
        }

        started = true
        AllocationRecorder.addSampler(this)
    }

    fun stop() {
        if (!started) {
            return
        }

        started = false
        AllocationRecorder.removeSampler(this)
    }

    fun clear() {
        data.clear()
    }

    fun stats() = data

    override fun sampleAllocation(count: Int, descriptor: String, instance: Any, size: Long) {
        val type = instance::class.java

        val frame: StackWalker.StackFrame = walker.walk { frame ->
            frame.filter {
                it.toStackTraceElement().className.startsWith("io.ktor")
            }.findFirst()
        }.takeIf { it.isPresent }?.get() ?: return

        val fileName = "${frame.fileName}:${frame.lineNumber}"
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size)
    }
}
