package io.ktor.benchmarks

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

fun readChannel(
    size: Int,
    coroutineContext: CoroutineContext = Dispatchers.IO
): ByteReadChannel {
    return CoroutineScope(coroutineContext).writer(CoroutineName("file-reader") + coroutineContext, autoFlush = false) {
        var x = 0
        val chunk = ByteArray(4000)
        while (x < size) {
            val chunkSize = minOf(size - x, chunk.size)
            channel.writeFully(chunk, 0, chunkSize)

            x += chunkSize
        }
    }.channel
}
