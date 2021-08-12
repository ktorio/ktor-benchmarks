/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

/**
 * This is a fake coroutine stack frame. It is reported by [SuspendFunctionGun] when the debug agent
 * is trying to probe jobs state by peeking frames when the coroutine is running at the same time
 * and the frames sequence is concurrently changed.
 */
internal object StackWalkingFailedFrame : CoroutineStackFrame, Continuation<Nothing> {
    override val callerFrame: CoroutineStackFrame? get() = null

    override fun getStackTraceElement(): StackTraceElement? {
        TODO()
    }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Nothing>) {
        StackWalkingFailed.failedToCaptureStackFrame()
    }
}
