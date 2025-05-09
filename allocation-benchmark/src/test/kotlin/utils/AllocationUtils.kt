package benchmarks.utils

import benchmarks.*
import io.ktor.server.engine.*
import utils.benchmarks.normalized

public val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

fun measureServerMemory(engine: String, requestCount: Number, block: () -> Unit): AllocationData {
    lateinit var server: EmbeddedServer<*, *>

    return AllocationTracker.measureAllocations(
        count = requestCount,
        prepare = { server = startServer(engine) },
        cleanup = { server.stop(1000, 1000) },
        block = block
    )
}

inline fun AllocationTracker.measureAllocations(
    count: Number,
    warmupSize: Int = WARMUP_SIZE,
    prepare: () -> Unit,
    cleanup: () -> Unit,
    block: () -> Unit,
): AllocationData {
    prepare()

    try {
        repeat(warmupSize) { block() }

        start()
        repeat(count.toInt()) { block() }
        stop()
    } finally {
        cleanup()
    }

    return stats().normalized(count.toLong())
}

fun startServer(engine: String) = server(engine)