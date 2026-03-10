package benchmarks

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler

private val IGNORED_DESCRIPTORS = listOf(
    "com/google",
    "benchmarks"
)
private val MAX_FRAMES = 20
private val EXTERNAL_FRAMES_DOWN = 0

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

        if (IGNORED_DESCRIPTORS.any(descriptor::startsWith)) return

        val frames = walker.walk { frames -> frames.toList() }
        if (frames.none { it.isKtor() }) return

        val firstKtorFrame = (frames.indexOfFirst { it.isKtor() }).coerceAtLeast(0)
        val lastKtorFrame = frames.indexOfLast { it.isKtor() }
        val cutPoint = ((lastKtorFrame + 1)..<frames.size)
            .firstOrNull { i -> frames[i].isCutPoint() }
        val frame = frames[firstKtorFrame]
        val stackTrace = frames.subList(
            (firstKtorFrame - EXTERNAL_FRAMES_DOWN).coerceAtLeast(0),
            cutPoint ?: frames.size,
        ).asSequence().take(MAX_FRAMES).map { it.format() }.toList()
        val fileName = frame.fileName
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size, stackTrace)
    }

    private fun StackWalker.StackFrame.isKtor() =
        toStackTraceElement().className.startsWith("io.ktor")

    private val CUT_FRAME_REGEX = Regex("^(?:kotlinx?\\.coroutines\\.|benchmarks\\.).*")

    private fun StackWalker.StackFrame.isCutPoint() =
        toStackTraceElement().className.matches(CUT_FRAME_REGEX)

    private fun StackWalker.StackFrame.format(): String {
        val lineNumber = if (isKtor()) lineNumber.toString() else "*"
        return "$fileName:$lineNumber ${methodName.orEmpty()}"
    }
}
