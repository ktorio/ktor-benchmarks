package io.ktor.benchmarks

import io.ktor.benchmarks.dispatchers.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import org.openjdk.jmh.annotations.*
import java.io.*
import java.nio.*

val dispatcher = newFixedThreadPoolContext(1, "FOOO1")
val dispatcher2 = newFixedThreadPoolContext(1, "FOOO2")

val simpleDispatcher = HotLoopDispatcher()
val simpleDispatcher2 = HotLoopDispatcher()

val blockingDispatcher = BlockingQueueDispatcher()
val blockingDispatcher2 = BlockingQueueDispatcher()

val ioDispatcher = IOCoroutineDispatcher(1)
val ioDispatcher2 = IOCoroutineDispatcher(1)

@OptIn(InternalCoroutinesApi::class)
val experimentalDispatcher = HighThroughputDispatcher(8, 32, "FOOO3")

@OptIn(InternalCoroutinesApi::class)
val experimentalDispatcher2 = HighThroughputDispatcher(8, 32, "FOOO4")

@OptIn(InternalCoroutinesApi::class)
val hugeExperimentalDispatcher = ExperimentalCoroutineDispatcher(100, 100, "FOOO5")

@OptIn(InternalCoroutinesApi::class)
val hugeExperimentalDispatcher2 = ExperimentalCoroutineDispatcher(100, 100, "FOOO6")

@State(Scope.Benchmark)
class FileBenchmarks {
    private lateinit var testFile: File
    private lateinit var buffer: ByteBuffer

    @Setup
    fun setup() {
        testFile = File.createTempFile("performance", "test")
        testFile.writer().use {
            for (i in 0..1024 * 1024 * 128) //~100M
                it.append('x')
        }

        buffer = ByteBuffer.allocate(4096)
    }

    @TearDown
    fun cleanup() {
        testFile.delete()
    }

    @Benchmark
    fun testKtorFileReadInIODispatcher(): Long = runBlocking(Dispatchers.IO) {
        var size = 0L
        val channel = testFile.readChannel()
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInFixedDispatcher(): Long = runBlocking {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = dispatcher)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInHotDispatcher(): Long = runBlocking {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = simpleDispatcher)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInBlocking2BlockingDispatcher(): Long = runBlocking(blockingDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = blockingDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInHot2HotDispatcher(): Long = runBlocking(simpleDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = simpleDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInIo2IoDispatcher(): Long = runBlocking(ioDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = ioDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInFixed2FixedDispatcher(): Long = runBlocking(dispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = dispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadInExp2ExpDispatcher(): Long = runBlocking(experimentalDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = experimentalDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }


    @Benchmark
    fun testKtorFileReadInHugeExp2HugeExpDispatcher(): Long = runBlocking(hugeExperimentalDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = hugeExperimentalDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileRead(): Long = runBlocking {
        var size = 0L
        val channel = testFile.readChannel()
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFileReadUnconfined(): Long = runBlocking {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = Dispatchers.Unconfined)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testJvmStreamRead(): Long {
        var size = 0L
        testFile.inputStream().buffered().use { stream ->
            while (true) {
                val read = stream.read(buffer.array())
                if (read == -1) break
                size += read
            }
        }
        return size
    }

    @Benchmark
    fun testJvmRandomFileRead(): Long {
        var size = 0L
        RandomAccessFile(testFile, "r").use { file ->
            file.channel.use { channel ->
                while (true) {
                    val read = channel.read(buffer)
                    buffer.clear()
                    if (read == -1) break
                    size += read
                }
            }
        }

        return size
    }

    @Benchmark
    fun testJvmStreamRead100(): Long {
        return List(100) {
            var size = 0L
            testFile.inputStream().buffered().use { stream ->
                while (true) {
                    val read = stream.read(buffer.array())
                    if (read == -1) break
                    size += read
                }
            }
            size
        }.sum()
    }

    @Benchmark
    fun testFilesReadChannel100() = runBlocking {
        val channels: List<ByteReadChannel> = List(100) {
            testFile.readChannel()
        }

        return@runBlocking channels.map {
            async {
                var size = 0
                val buffer = ByteBuffer.allocate(4000)
                while (true) {
                    val read = it.readAvailable(buffer)
                    buffer.clear()
                    if (read == -1) break
                    size += read
                }
                size
            }
        }.awaitAll().sum()
    }

    @Benchmark
    fun testKtorFakeFileRead(): Long = runBlocking {
        var size = 0L
        val channel = readChannel(128 * 1024 * 1024)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }

    @Benchmark
    fun testKtorFakeFileReadInHot(): Long = runBlocking(ioDispatcher) {
        var size = 0L
        val channel = testFile.readChannel(coroutineContext = ioDispatcher2)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            buffer.clear()
            size += read
        }

        channel.cancel()
        return@runBlocking size
    }
}
