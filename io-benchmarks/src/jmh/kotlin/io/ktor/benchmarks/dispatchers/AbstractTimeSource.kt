/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

// Need InlineOnly for efficient bytecode on Android
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "NOTHING_TO_INLINE")

package io.ktor.benchmarks.dispatchers

import kotlin.internal.*

internal abstract class AbstractTimeSource {
    abstract fun currentTimeMillis(): Long
    abstract fun nanoTime(): Long
    abstract fun wrapTask(block: Runnable): Runnable
    abstract fun trackTask()
    abstract fun unTrackTask()
    abstract fun registerTimeLoopThread()
    abstract fun unregisterTimeLoopThread()
    abstract fun parkNanos(blocker: Any, nanos: Long) // should return immediately when nanos <= 0
    abstract fun unpark(thread: Thread)
}

// For tests only
// @JvmField: Don't use JvmField here to enable R8 optimizations via "assumenosideeffects"
internal var timeSource: AbstractTimeSource? = null

@InlineOnly
internal inline fun trackTask() {
    timeSource?.trackTask()
}

@InlineOnly
internal inline fun unTrackTask() {
    timeSource?.unTrackTask()
}

