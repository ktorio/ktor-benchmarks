package benchmarks

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler
import kotlin.streams.*

private val IGNORED_DESCRIPTORS = mutableListOf<String>(
    "com/google",
    "benchmarks"
)

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

        if (IGNORED_DESCRIPTORS.any { descriptor.startsWith(it) }) return

        val frames = walker.walk { frames ->
            frames.toList()
        }.takeIf { stack -> stack.any { it.isKtor() } } ?: return

        val firstKtorClass = frames.indexOfFirst { it.isKtor() }
        val frame = frames[firstKtorClass]
        val stackTrace = frames.subList(maxOf(firstKtorClass, 0), frames.size)
            .asSequence()
            .take(20)
            .map { "${it.fileName}:${it.lineNumber} ${it.methodName ?: ""}" }
            .toList()
        val fileName = frame.fileName
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size, stackTrace)
    }

    private fun StackWalker.StackFrame.isKtor() =
        toStackTraceElement().className.startsWith("io.ktor")
}
