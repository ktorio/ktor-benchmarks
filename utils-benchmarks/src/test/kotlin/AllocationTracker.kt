import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.Sampler
import kotlin.streams.toList


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
            frames.filter {
                it.toStackTraceElement().className.startsWith("io.ktor")
            }.toList()
        }.takeIf { it.isNotEmpty() } ?: return

        val frame = frames.first()
        val stackTrace = frames.map { "${it.fileName}:${it.lineNumber}" }
        val fileName = frame.fileName
        val packageData = data.add(fileName) { LocationInfo(fileName) }
        packageData.add(type, size, stackTrace)
    }
}