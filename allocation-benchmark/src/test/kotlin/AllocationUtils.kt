@file:OptIn(EngineAPI::class)

package benchmarks

import io.ktor.server.engine.*

private val SAVE_REPORT: Boolean = System.getProperty("SAVE_REPORT") == "true"

fun measureMemory(testName: String, engine: String, block: () -> Unit) {
    AllocationTracker.clear()
    val server = startServer(engine)
    try {
        warmup()
        AllocationTracker.start()
        block()
        AllocationTracker.stop()
    } finally {
        stopServer(server)
    }

    val reportName = "$testName[$engine]"

    if (SAVE_REPORT) {
        saveReport(reportName, AllocationTracker.stats())
        return
    }

    checkAllocationDataIsSame(loadReport(reportName), AllocationTracker.stats())
}

private fun warmup() {
    repeat(WARMUP_SIZE) {
        makeRequest()
    }
}

fun startServer(engine: String): BaseApplicationEngine = server(engine)

fun stopServer(server: BaseApplicationEngine) {
    server.stop(1000, 1000)
}