package io.ktor.benchmarks.dispatchers

import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.internal.*

// number of processors at startup for consistent prop initialization
internal val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()

internal fun systemProp(
    propertyName: String
): String? = try {
    System.getProperty(propertyName)
} catch (e: SecurityException) {
    null
}

internal fun systemProp(
    propertyName: String,
    defaultValue: Long,
    minValue: Long = 1,
    maxValue: Long = Long.MAX_VALUE
): Long {
    val value = systemProp(propertyName) ?: return defaultValue
    val parsed = value.toLongOrNull()
        ?: error("System property '$propertyName' has unrecognized value '$value'")
    if (parsed !in minValue..maxValue) {
        error("System property '$propertyName' should be in range $minValue..$maxValue, but is '$parsed'")
    }
    return parsed
}

// 100us as default
@JvmField
internal val WORK_STEALING_TIME_RESOLUTION_NS = systemProp(
    "kotlinx.coroutines.scheduler.resolution.ns", 100000L
)

@JvmField
internal val BLOCKING_DEFAULT_PARALLELISM = systemProp(
    "kotlinx.coroutines.scheduler.blocking.parallelism", 16
)

internal fun systemProp(
    propertyName: String,
    defaultValue: Int,
    minValue: Int = 1,
    maxValue: Int = Int.MAX_VALUE
): Int = systemProp(propertyName, defaultValue.toLong(), minValue.toLong(), maxValue.toLong()).toInt()

// NOTE: we coerce default to at least two threads to give us chances that multi-threading problems
// get reproduced even on a single-core machine, but support explicit setting of 1 thread scheduler if needed.
@JvmField
internal val CORE_POOL_SIZE = systemProp(
    "kotlinx.coroutines.scheduler.core.pool.size",
    AVAILABLE_PROCESSORS.coerceAtLeast(2), // !!! at least two here
    minValue = HighThroughputScheduler.MIN_SUPPORTED_POOL_SIZE
)

@JvmField
internal val MAX_POOL_SIZE = systemProp(
    "kotlinx.coroutines.scheduler.max.pool.size",
    (AVAILABLE_PROCESSORS * 128).coerceIn(
        CORE_POOL_SIZE,
        HighThroughputScheduler.MAX_SUPPORTED_POOL_SIZE
    ),
    maxValue = HighThroughputScheduler.MAX_SUPPORTED_POOL_SIZE
)

@JvmField
internal val IDLE_WORKER_KEEP_ALIVE_NS = TimeUnit.SECONDS.toNanos(
    systemProp("kotlinx.coroutines.scheduler.keep.alive.sec", 60L)
)

internal val ASSERTIONS_ENABLED = CoroutineId::class.java.desiredAssertionStatus()

internal inline fun assert(value: () -> Boolean) {
    if (ASSERTIONS_ENABLED && !value()) throw AssertionError()
}

private const val DEBUG_THREAD_NAME_SEPARATOR = " @"

internal data class CoroutineId(
    val id: Long
) : ThreadContextElement<String>, AbstractCoroutineContextElement(CoroutineId) {
    companion object Key : CoroutineContext.Key<CoroutineId>
    override fun toString(): String = "CoroutineId($id)"

    override fun updateThreadContext(context: CoroutineContext): String {
        val coroutineName = context[CoroutineName]?.name ?: "coroutine"
        val currentThread = Thread.currentThread()
        val oldName = currentThread.name
        var lastIndex = oldName.lastIndexOf(DEBUG_THREAD_NAME_SEPARATOR)
        if (lastIndex < 0) lastIndex = oldName.length
        currentThread.name = buildString(lastIndex + coroutineName.length + 10) {
            append(oldName.substring(0, lastIndex))
            append(DEBUG_THREAD_NAME_SEPARATOR)
            append(coroutineName)
            append('#')
            append(id)
        }
        return oldName
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String) {
        Thread.currentThread().name = oldState
    }
}

internal var schedulerTimeSource: SchedulerTimeSource = NanoTimeSource

internal abstract class SchedulerTimeSource {
    abstract fun nanoTime(): Long
}

internal object NanoTimeSource : SchedulerTimeSource() {
    override fun nanoTime() = System.nanoTime()
}
