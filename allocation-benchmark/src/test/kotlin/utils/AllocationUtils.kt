package benchmarks.utils

import benchmarks.AllocationData
import benchmarks.AllocationTracker
import benchmarks.TRACKED_WARMUP_SIZE
import benchmarks.server
import utils.benchmarks.normalized

val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

internal inline fun AllocationTracker.measureAllocations(
    count: Number,
    prepare: () -> Unit,
    cleanup: () -> Unit,
    block: () -> Unit,
): AllocationData {
    prepare()

    try {
        warmupUntilJitStabilized(block = block)

        clear()
        start()
        repeat(TRACKED_WARMUP_SIZE) { block() }

        clear()
        repeat(count.toInt()) { block() }
    } finally {
        stop()
        cleanup()
    }

    return stats().normalized(count.toLong())
}

fun startServer(engine: String) = server(engine)