package benchmarks

public val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

fun measureMemory(engine: String, block: () -> Unit): AllocationData {
    AllocationTracker.clear()
    val server = startServer(engine)
    try {
        warmup()
        AllocationTracker.start()
        block()
        AllocationTracker.stop()
    } finally {
        server.stop(1000, 1000)
    }

    return AllocationTracker.stats()
}

private fun warmup() {
    repeat(WARMUP_SIZE) {
        makeRequest()
    }
}

fun startServer(engine: String) = server(engine)