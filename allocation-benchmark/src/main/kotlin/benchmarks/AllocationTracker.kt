package benchmarks

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler

private val IGNORED_DESCRIPTORS = listOf(
    "com/google",
    "benchmarks"
)
private const val MAX_FRAMES = 20
private const val EXTERNAL_FRAMES_DOWN = 0

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

        var firstKtor = -1
        var lastKtor = -1
        val frames = ArrayList<StackWalker.StackFrame>(MAX_FRAMES)
        walker.walk { stream ->
            for (frame in stream) {
                if (frame.isKtor()) {
                    if (firstKtor == -1) firstKtor = frames.size
                    lastKtor = frames.size
                }
                frames.add(frame)
                if (firstKtor != -1 && frames.size - firstKtor >= MAX_FRAMES) break
            }
        }

        if (firstKtor == -1) return

        val startFrame = (firstKtor - EXTERNAL_FRAMES_DOWN).coerceAtLeast(0)
        val maxFrame = (startFrame + MAX_FRAMES).coerceAtMost(frames.size)
        val endFrame = ((lastKtor + 1)..<maxFrame)
            .find { frames[it].isCutPoint() } ?: maxFrame

        val frame = frames[firstKtor]
        val stackTrace = frames.subList(startFrame, endFrame).map { it.format() }
        val fileName = frame.fileName
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size, stackTrace)
    }

    private fun StackWalker.StackFrame.isKtor() =
        className.startsWith("io.ktor")

    private val CUT_FRAME_REGEX = Regex("^(?:kotlinx?\\.coroutines\\.|benchmarks\\.).*")

    private fun StackWalker.StackFrame.isCutPoint() =
        className.matches(CUT_FRAME_REGEX)

    private val formatCache = ThreadLocal.withInitial<HashMap<Long, String>> { HashMap() }

    private fun StackWalker.StackFrame.format(): String {
        val method = methodName.orEmpty()
        val key = (className.hashCode().toLong() shl 32) or ((lineNumber xor method.hashCode()).toLong() and 0xFFFFFFFFL)
        // Reduce allocations on hot path
        return formatCache.get().getOrPut(key) {
            val lineStr = if (isKtor()) lineNumber.toString() else "*"
            "$fileName:$lineStr $method"
        }
    }
}
