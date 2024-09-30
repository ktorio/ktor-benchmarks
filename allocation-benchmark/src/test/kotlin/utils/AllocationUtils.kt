package benchmarks.utils

import benchmarks.AllocationData
import benchmarks.AllocationTracker
import benchmarks.WARMUP_SIZE
import benchmarks.server

public val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

fun measureMemory(engine: String, requestCount: Number, block: () -> Unit): AllocationData {
    AllocationTracker.clear()
    val server = startServer(engine)
    try {
        repeat(WARMUP_SIZE) {
            block()
        }
        AllocationTracker.start()
        repeat(requestCount.toInt()) {
            block()
        }
        AllocationTracker.stop()
    } finally {
        server.stop(1000, 1000)
    }

    return AllocationTracker.stats()
}

fun startServer(engine: String) = server(engine)