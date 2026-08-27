package benchmarks.utils

import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val MINIMUM_JIT_WARMUP_REQUEST_COUNT = 300
private const val JIT_WARMUP_BATCH_SIZE = 50
private const val REQUIRED_STABLE_COMPILATION_BATCH_COUNT = 3
private val MAXIMUM_JIT_WARMUP_DURATION = 60.seconds

internal data class JitWarmupResult(
    val requestCount: Int,
    val elapsedMilliseconds: Long,
    val compilationTimeDeltaMilliseconds: Long,
)

internal inline fun warmupUntilJitStabilized(
    noinline compilationTimeMilliseconds: () -> Long = compilationTimeProvider(),
    timeSource: TimeSource = TimeSource.Monotonic,
    block: () -> Unit,
): JitWarmupResult {
    val startedAt = timeSource.markNow()
    val deadline = startedAt + MAXIMUM_JIT_WARMUP_DURATION
    val initialCompilationTime = compilationTimeMilliseconds()
    var previousCompilationTime = initialCompilationTime
    var stableCompilationBatchCount = 0
    var requestCount = 0

    while (stableCompilationBatchCount < REQUIRED_STABLE_COMPILATION_BATCH_COUNT) {
        check(deadline.hasNotPassedNow()) { "JIT compilation did not stabilize within $MAXIMUM_JIT_WARMUP_DURATION" }

        repeat(JIT_WARMUP_BATCH_SIZE) { block() }
        requestCount += JIT_WARMUP_BATCH_SIZE

        val currentCompilationTime = compilationTimeMilliseconds()
        if (
            requestCount >= MINIMUM_JIT_WARMUP_REQUEST_COUNT &&
            currentCompilationTime == previousCompilationTime
        ) {
            stableCompilationBatchCount += 1
        } else {
            stableCompilationBatchCount = 0
            previousCompilationTime = currentCompilationTime
        }
    }

    return JitWarmupResult(
        requestCount = requestCount,
        elapsedMilliseconds = startedAt.elapsedNow().inWholeMilliseconds,
        compilationTimeDeltaMilliseconds = previousCompilationTime - initialCompilationTime,
    ).reported()
}

private fun JitWarmupResult.reported(): JitWarmupResult {
    println(
        "JIT warmup: $requestCount requests in ${elapsedMilliseconds}ms; " +
                "compilation time +${compilationTimeDeltaMilliseconds}ms; stabilized"
    )
    return this
}

private fun compilationTimeProvider(): () -> Long {
    val compilationBean = checkNotNull(ManagementFactory.getCompilationMXBean()) {
        "JIT compilation monitoring is unavailable"
    }
    check(compilationBean.isCompilationTimeMonitoringSupported) {
        "JIT compilation time monitoring is unsupported"
    }
    return { compilationBean.totalCompilationTime }
}
