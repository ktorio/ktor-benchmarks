package io.ktor.benchmarks.dispatchers

import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.*
import kotlin.coroutines.*


open class HighThroughputDispatcher(
    private val corePoolSize: Int,
    private val maxPoolSize: Int,
    private val idleWorkerKeepAliveNs: Long,
    private val schedulerName: String = "HTS"
) : ExecutorCoroutineDispatcher() {
    constructor(
        corePoolSize: Int = CORE_POOL_SIZE,
        maxPoolSize: Int = MAX_POOL_SIZE,
        schedulerName: String = "HTS"
    ) : this(corePoolSize, maxPoolSize, IDLE_WORKER_KEEP_ALIVE_NS, schedulerName)

    override val executor: Executor
        get() = coroutineScheduler

    // This is variable for test purposes, so that we can reinitialize from clean state
    private var coroutineScheduler = createScheduler()

    override fun dispatch(context: CoroutineContext, block: Runnable): Unit =
        coroutineScheduler.dispatch(block)

    @OptIn(InternalCoroutinesApi::class)
    override fun dispatchYield(context: CoroutineContext, block: Runnable): Unit =
        coroutineScheduler.dispatch(block, tailDispatch = true)

    override fun close(): Unit = coroutineScheduler.close()

    override fun toString(): String {
        return "${super.toString()}[scheduler = $coroutineScheduler]"
    }

    /**
     * Creates a coroutine execution context with limited parallelism to execute tasks which may potentially block.
     * Resulting [CoroutineDispatcher] doesn't own any resources (its threads) and provides a view of the original [ExperimentalCoroutineDispatcher],
     * giving it additional hints to adjust its behaviour.
     *
     * @param parallelism parallelism level, indicating how many threads can execute tasks in the resulting dispatcher parallel.
     */
    fun blocking(parallelism: Int = BLOCKING_DEFAULT_PARALLELISM): CoroutineDispatcher {
        require(parallelism > 0) { "Expected positive parallelism level, but have $parallelism" }
        return LimitingDispatcher1(this, parallelism, null, TASK_PROBABLY_BLOCKING)
    }

    /**
     * Creates a coroutine execution context with limited parallelism to execute CPU-intensive tasks.
     * Resulting [CoroutineDispatcher] doesn't own any resources (its threads) and provides a view of the original [ExperimentalCoroutineDispatcher],
     * giving it additional hints to adjust its behaviour.
     *
     * @param parallelism parallelism level, indicating how many threads can execute tasks in the resulting dispatcher parallel.
     */
    fun limited(parallelism: Int): CoroutineDispatcher {
        require(parallelism > 0) { "Expected positive parallelism level, but have $parallelism" }
        require(parallelism <= corePoolSize) { "Expected parallelism level lesser than core pool size ($corePoolSize), but have $parallelism" }
        return LimitingDispatcher1(this, parallelism, null, TASK_NON_BLOCKING)
    }

    internal fun dispatchWithContext(block: Runnable, context: TaskContext, tailDispatch: Boolean) {
        coroutineScheduler.dispatch(block, context, tailDispatch)
    }

    private fun createScheduler() =
        HighThroughputScheduler(corePoolSize, maxPoolSize, idleWorkerKeepAliveNs, schedulerName)

}
