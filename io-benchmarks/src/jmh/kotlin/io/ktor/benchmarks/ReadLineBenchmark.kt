package io.ktor.benchmarks

import io.ktor.utils.io.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*

private const val MB = 1024 * 1024

@OptIn(InternalAPI::class)
@State(Scope.Benchmark)
class ReadLineBenchmark {

    // Format: "<number of lines> x <line size>"
    @Param(
        "200_000 x 10",
        "1_000 x $MB",
    )
    var params: String = ""

    @Param("true", "false")
    var noCr: Boolean = true

    private lateinit var channel : ByteReadChannel

    @Setup(Level.Invocation)
    fun setup() {
        val (numberOfLines, lineSize) = params.split(" x ").map { it.replace("_", "").toInt() }
        val line = "A".repeat(lineSize) + "\n"

        @OptIn(DelicateCoroutinesApi::class)
        channel = GlobalScope.writer(Dispatchers.IO) {
            repeat(numberOfLines) {
                channel.writeStringUtf8(line)
            }
        }.channel
    }

    @Benchmark
    fun readLine() = runBlocking {
        val lineEnding = if (noCr) LineEndingMode.LF + LineEndingMode.CRLF else LineEndingMode.Any
        val out = StringBuilder()
        var count = 0

        while (channel.readUTF8LineTo(out, lineEnding = lineEnding)) {
            count++
            out.clear()
        }

        count
    }

    @TearDown(Level.Invocation)
    fun tearDown() {
        channel.cancel()
    }
}