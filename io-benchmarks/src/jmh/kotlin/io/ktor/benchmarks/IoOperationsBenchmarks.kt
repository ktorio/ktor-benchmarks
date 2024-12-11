package io.ktor.benchmarks

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.openjdk.jmh.annotations.*
import kotlin.test.*

@State(Scope.Benchmark)
class IoOperationsBenchmarks {

    @Benchmark
    fun readLines() = runBlocking {
        var lineNumber = 0
        var count = 0
        val numberOfLines = 100_000
        val channel = writer(Dispatchers.IO) {
            for (line in generateSequence { "line ${lineNumber++}\n" }.take(numberOfLines))
                channel.writeStringUtf8(line)
        }.channel
        val out = StringBuilder()
        while (channel.readUTF8LineTo(out) && count < numberOfLines)
            count++

        assertEquals(numberOfLines, count)
    }

}