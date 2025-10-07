// ABOUTME: Client benchmark testing concurrent HTTP requests against a local Netty server
// ABOUTME: Measures throughput of different Ktor client engines (CIO, Apache, OkHttp, Java)

package io.ktor.benchmarks

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import okhttp3.*
import java.net.Socket
import kotlin.time.*

data class BenchmarkConfig(
    val batchCount: Int = 10,
    val batchSize: Int = 1000,
    val threadCount: Int = 100,
    val port: Int = 8080
)

data class BenchmarkResult(
    val engineName: String,
    val totalTime: Duration,
    val requestCount: Int,
    val requestsPerSecond: Double
)

@OptIn(ExperimentalTime::class)
suspend fun waitForServerHealth(port: Int, maxAttempts: Int = 30): Boolean {
    val healthClient = HttpClient(CIO) {
        expectSuccess = false
    }

    println("Waiting for server to start...")
    var serverReady = false
    repeat(maxAttempts) {
        try {
            val response = healthClient.get("http://localhost:$port/health")
            if (response.status.isSuccess()) {
                serverReady = true
                println("Server is ready")
                return@repeat
            }
        } catch (e: Exception) {
            delay(100)
        }
    }
    healthClient.close()

    return serverReady
}

@OptIn(ExperimentalTime::class)
suspend fun runBenchmark(
    engineName: String,
    config: BenchmarkConfig,
    block: suspend (BenchmarkConfig) -> Unit
): BenchmarkResult = withContext(Dispatchers.Default) {
    println("\n=== Running benchmark for $engineName ===")
    println("Running ${config.batchCount}x ${config.batchSize} requests…")

    val time = measureTime {
        repeat(config.batchCount) { i ->
            coroutineScope {
                repeat(config.batchSize) {
                    launch {
                        block(config)
                    }
                }
            }
            println("Batch ${i + 1} completed")
        }
    }

    val totalRequests = config.batchCount * config.batchSize
    val timeInSeconds = time.inWholeMilliseconds / 1000.0
    val requestsPerSecond = if (timeInSeconds > 0) totalRequests / timeInSeconds else 0.0

    BenchmarkResult(engineName, time, totalRequests, requestsPerSecond)
}

fun printResults(results: List<BenchmarkResult>) {
    println("\n" + "=".repeat(70))
    println("BENCHMARK RESULTS")
    println("=".repeat(70))
    println()
    println("%-20s %15s %15s %15s".format("Engine", "Total Time", "Requests", "Req/sec"))
    println("-".repeat(70))

    results.forEach { result ->
        println(
            "%-20s %15s %15d %15.2f".format(
                result.engineName,
                result.totalTime.toString(),
                result.requestCount,
                result.requestsPerSecond
            )
        )
    }

    println("=".repeat(70))

    val fastest = results.minByOrNull { it.totalTime }
    if (fastest != null) {
        println("\nFastest: ${fastest.engineName} (${fastest.totalTime})")
    }
}

@OptIn(ExperimentalTime::class)
suspend fun main(args: Array<String>) {
    val config = BenchmarkConfig()

    val server = createBenchmarkServer(config.port)
    server.start(wait = false)

    if (!waitForServerHealth(config.port)) {
        println("Server failed to start")
        server.stop(1000, 5000)
        return
    }

    val results = mutableListOf<BenchmarkResult>()

    try {
        val result = runBenchmark("Raw HTTP", config) { cfg ->
            withContext(Dispatchers.IO) {
                val request = buildString {
                    append("GET /benchmarks/hello HTTP/1.1\r\n")
                    append("Host: localhost:${cfg.port}\r\n")
                    append("Connection: keep-alive\r\n")
                    append("\r\n")
                }.toByteArray()

                Socket("localhost", cfg.port).use { socket ->
                    socket.getOutputStream().write(request)
                    socket.getOutputStream().flush()

                    socket.getInputStream().bufferedReader().use { reader ->
                        reader.readLine()
                        var line: String?
                        var contentLength = 0

                        while (reader.readLine().also { line = it } != null && !line.isNullOrEmpty()) {
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substringAfter(":").trim().toInt()
                            }
                        }

                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        String(body)
                    }
                }
            }
        }
        results.add(result)
    } catch (e: Exception) {
        println("Error running Raw HTTP benchmark: ${e.message}")
        e.printStackTrace()
    }

    println("\nWaiting 10 seconds before next benchmark...")
    delay(10000)

    val engines = listOf(
//        CIO to "CIO",
        Apache to "Apache",
        Java to "Java",
        io.ktor.client.engine.okhttp.OkHttp.config {
            config {
                addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header(HttpHeaders.AcceptEncoding, "").build())
                }
                protocols(listOf(Protocol.HTTP_1_1))
            }
        } to "OkHttp"
    )

    for ((engineFactory, engineName) in engines) {
        try {
            val client = HttpClient(engineFactory) {
                expectSuccess = false
                engine {
                    if (this is CIOEngineConfig) {
                        maxConnectionsCount = 1000
                    }
                }
            }

            val result = runBenchmark(engineName, config) { cfg ->
                client.get("http://localhost:${cfg.port}/benchmarks/hello").bodyAsText()
            }
            results.add(result)

            client.close()

            println("\nWaiting 10 seconds before next benchmark...")
            delay(10000)
        } catch (e: Exception) {
            println("Error running benchmark for $engineName: ${e.message}")
            e.printStackTrace()
        }
    }

    printResults(results)

    server.stop(1000, 5000)
}
