package benchmarks.utils

import benchmarks.AllocationData
import benchmarks.AllocationTracker
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
        repeat(count.toInt()) { block() }
        stop()
    } finally {
        cleanup()
    }

    return stats().normalized(count.toLong())
}

fun startServer(engine: String) = server(engine)