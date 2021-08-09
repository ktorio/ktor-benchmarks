package io.ktor.benchmarks

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@State(Scope.Benchmark)
class SocketBenchmarks {
    val buffer = ByteArray(4088)
    lateinit var serverSocket: ServerSocket
    lateinit var executor: ExecutorService
    lateinit var ioSelectorManager: ActorSelectorManager
    @Setup
    fun setup() {
        executor = Executors.newSingleThreadExecutor()
        serverSocket = ServerSocket(4200)
        ioSelectorManager  = ActorSelectorManager(Dispatchers.IO)

        executor.submit {
            val buffer = ByteArray(4096)
            while (!Thread.interrupted()) {
                serverSocket.accept().use { socket ->
                    socket.getInputStream().use { stream ->
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) {
                                socket.close()
                                return@use
                            }
                        }
                    }
                }
            }
        }
    }

    @TearDown
    fun cleanup() {
        serverSocket.close()
        executor.shutdown()
        ioSelectorManager.close()
    }

    @Benchmark
    fun testKtorSocketWrite() = runBlocking {
        val buffer = ByteArray(4088)
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        aSocket(selectorManager).tcp().connect("localhost", 4200).use { socket ->
            val channel = socket.openWriteChannel(autoFlush = true)
            for (i in 1..32768) { //~100M
                channel.writeFully(buffer, 0, buffer.size)
            }

            channel.close()
        }
    }

    @Benchmark
    fun testKtorSocketWriteWithoutAutoFlush() = runBlocking {
        val buffer = ByteArray(4088)
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        aSocket(selectorManager).tcp().connect("localhost", 4200).use { socket ->
            val channel = socket.openWriteChannel(autoFlush = false)
            for (i in 1..32768) { //~100M
                channel.writeFully(buffer, 0, buffer.size)
            }

            channel.close()
        }
    }

    @Benchmark
    fun testJvmSocketWrite() {
        Socket("localhost", 4200).use {
            it.getOutputStream().use { stream ->
                for (i in 1..32768) { //~100M
                    stream.write(buffer, 0, buffer.size)
                }
            }
        }
    }
}