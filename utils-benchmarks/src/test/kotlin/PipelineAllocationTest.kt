import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class PipelineAllocationTest {
    val pipeline = MyPipeline().apply { setup() }
    val debugPipeline = MyDebugPipeline().apply { setup() }

    @Test
    fun measurePipelineAllocations() = runBlocking {
        pipeline.execute("", "")
        AllocationTracker.clear()
        AllocationTracker.start()
        pipeline.execute("", "")
        AllocationTracker.stop()
        saveReport("allocations", AllocationTracker.stats())
    }

    @Test
    fun measureDebugPipelineAllocations() = runBlocking {
        debugPipeline.execute("", "")
        AllocationTracker.clear()
        AllocationTracker.start()
        debugPipeline.execute("", "")
        AllocationTracker.stop()
        saveReport("debug-allocations", AllocationTracker.stats())
    }
}

private val serializer = Json {
    prettyPrint = true
}

fun saveReport(name: String, report: AllocationData) {
    val file = File("allocations/${name}.json")
    if (!file.exists()) {
        file.createNewFile()
    }

    val content = serializer.encodeToString(report)
    file.bufferedWriter().use {
        it.write(content)
    }
}
