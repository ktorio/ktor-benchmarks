package benchmarks

import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal object JitStabilizer {
    private const val MINIMUM_REQUEST_COUNT = 300
    private const val BATCH_SIZE = 50
    private const val REQUIRED_STABLE_BATCH_COUNT = 3
    private val TIMEOUT = 60.seconds

    @JvmStatic
    fun await(request: Runnable) {
        val compilationBean = checkNotNull(ManagementFactory.getCompilationMXBean()) {
            "JIT compilation monitoring is unavailable"
        }
        check(compilationBean.isCompilationTimeMonitoringSupported) {
            "JIT compilation time monitoring is unsupported"
        }

        val startedAt = TimeSource.Monotonic.markNow()
        val deadline = startedAt + TIMEOUT
        val initialCompilationTime = compilationBean.totalCompilationTime
        var previousCompilationTime = initialCompilationTime
        var stableBatchCount = 0
        var requestCount = 0

        while (stableBatchCount < REQUIRED_STABLE_BATCH_COUNT) {
            check(deadline.hasNotPassedNow()) {
                "JIT compilation did not stabilize within $TIMEOUT"
            }

            repeat(BATCH_SIZE) { request.run() }
            requestCount += BATCH_SIZE

            val currentCompilationTime = compilationBean.totalCompilationTime
            stableBatchCount = if (
                requestCount >= MINIMUM_REQUEST_COUNT &&
                currentCompilationTime == previousCompilationTime
            ) {
                stableBatchCount + 1
            } else {
                0
            }
            previousCompilationTime = currentCompilationTime
        }

        println(
            "JIT warmup: $requestCount requests in ${startedAt.elapsedNow()}; " +
                    "compilation time +${previousCompilationTime - initialCompilationTime}ms; stabilized"
        )
    }
}
