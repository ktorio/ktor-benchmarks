package io.ktor.benchmarks.dispatchers

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import kotlinx.coroutines.scheduling.*
import java.lang.Runnable


internal const val TASK_NON_BLOCKING = 0

// Open for tests
internal class GlobalQueue : LockFreeTaskQueue<Task>(singleConsumer = false)

internal abstract class Task(
    @JvmField var submissionTime: Long,
    @JvmField var taskContext: TaskContext
) : Runnable {
    constructor() : this(0, NonBlockingContext)
    inline val mode: Int get() = taskContext.taskMode // TASK_XXX
}

internal interface TaskContext {
    val taskMode: Int // TASK_XXX
    fun afterTask()
}

internal object NonBlockingContext : TaskContext {
    override val taskMode: Int = TASK_NON_BLOCKING

    override fun afterTask() {
        // Nothing for non-blocking context
    }
}

// Non-reusable Task implementation to wrap Runnable instances that do not otherwise implement task
internal class TaskImpl(
    @JvmField val block: Runnable,
    submissionTime: Long,
    taskContext: TaskContext
) :Task(submissionTime, taskContext) {
    override fun run() {
        try {
            block.run()
        } finally {
            taskContext.afterTask()
        }
    }

    override fun toString(): String =
        "Task[${block.classSimpleName}@${block.hexAddress}, $submissionTime, $taskContext]"
}

internal val Any.classSimpleName: String get() = this::class.java.simpleName
