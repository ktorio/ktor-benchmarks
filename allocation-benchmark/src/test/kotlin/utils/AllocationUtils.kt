package benchmarks.utils

import benchmarks.AllocationData
import benchmarks.AllocationTracker
import benchmarks.WARMUP_SIZE
import benchmarks.server
import utils.benchmarks.normalized

val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

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

        clear()
        start()
        repeat(count.toInt()) { block() }
        stop()
    } finally {
        cleanup()
    }

    return stats().normalized(count.toLong())
}

fun startServer(engine: String) = server(engine)