package benchmarks

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler
import kotlin.streams.*

private val IGNORED_DESCRIPTORS = mutableListOf<String>(
    "com/google",
    "benchmarks"
)
private val MAX_FRAMES = 20
private val EXTERNAL_FRAMES_DOWN = 1

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

        val firstKtorClass = (frames.indexOfFirst { it.isKtor() }).coerceAtLeast(0)
        val firstCoroutineResume = (firstKtorClass..<frames.size).firstOrNull { i -> frames[i].isCoroutine() }
        val frame = frames[firstKtorClass]
        val stackTrace = frames.subList(
            (firstKtorClass - EXTERNAL_FRAMES_DOWN).coerceAtLeast(0),
            firstCoroutineResume ?: frames.size
        ).asSequence().take(MAX_FRAMES).map { it.format() }.toList()
        val fileName = frame.fileName
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size, stackTrace)
    }

    private fun StackWalker.StackFrame.isKtor() =
        toStackTraceElement().className.startsWith("io.ktor")

    private fun StackWalker.StackFrame.isCoroutine() =
        toStackTraceElement().className.matches(Regex("^(?:Coroutine|Continuation).*"))

    private fun StackWalker.StackFrame.format() =
        "$fileName:$lineNumber ${methodName ?: ""}"
}
