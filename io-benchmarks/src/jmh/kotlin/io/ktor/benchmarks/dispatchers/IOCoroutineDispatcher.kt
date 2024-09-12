package io.ktor.benchmarks.dispatchers

import io.ktor.util.*
import io.ktor.util.internal.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Default ktor fixed size dispatcher for doing non-blocking I/O operations and selection
 */
class IOCoroutineDispatcher(private val nThreads: Int) : CoroutineDispatcher(), Closeable {
    @Suppress("DEPRECATION_ERROR")
    private val dispatcherThreadGroup = ThreadGroup("io-pool-group-sub")

    private val tasks = LockFreeLinkedListHead()

    init {
        require(nThreads > 0) { "nThreads should be positive but $nThreads specified" }
    }

    private val threads = Array(nThreads) {
        IOThread(it + 1, tasks, dispatcherThreadGroup)
    }

    init {
        threads.forEach {
            it.start()
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        IODispatchedTask(block).also { tasks.addLast(it) }
    }

    /**
     * Gracefully shutdown dispatcher.
     */
    override fun close() {
        if (tasks.prev is Poison) return
        tasks.addLastIfPrev(Poison()) { prev -> prev !is Poison }
    }


    private class IOThread(
        private val number: Int,
        private val tasks: LockFreeLinkedListHead,
        dispatcherThreadGroup: ThreadGroup
    ) : Thread(dispatcherThreadGroup, "io-thread-$number") {


        init {
            isDaemon = true
        }

        override fun run() {
            try {
                while (true) {
                    val task = tasks.removeFirstIfIsInstanceOf<Runnable>()
                    if (task == null) {
                        onSpinWait()
                        continue
                    }

                    try {
                        task.run()
                    } catch (t: Throwable) {
                        onException(ExecutionException("Task failed", t))
                    }
                }
            } catch (t: Throwable) {
                onException(ExecutionException("Thread pool worker failed", t))
            }
        }

        private fun onException(t: Throwable) {
            currentThread().uncaughtExceptionHandler.uncaughtException(this, t)
        }
    }

    private class Poison : LockFreeLinkedListNode()
    private class IODispatchedTask(val r: Runnable) : LockFreeLinkedListNode(), Runnable by r
}
