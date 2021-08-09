package io.ktor.benchmarks.dispatchers

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

class HotLoopDispatcher : CoroutineDispatcher() {
    val queue = ConcurrentLinkedQueue<Runnable>()

    val thread: Thread = Thread {
        while (true) {
            val task = queue.poll() ?: continue
            try {
                task.run()
            } catch (cause: Throwable) {
                println("cause $cause")
                cause.printStackTrace()
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }
}


class BlockingQueueDispatcher : CoroutineDispatcher() {
    val queue = LinkedBlockingQueue<Runnable>()

    val thread: Thread = Thread {
        while (true) {
            val task = queue.take()
            try {
                task.run()
            } catch (cause: Throwable) {
                println("cause $cause")
                cause.printStackTrace()
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }
}