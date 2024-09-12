/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.benchmarks

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
class RoutingBenchmark {
    private val testHost = TestApplication {
        routing {
            get("/short") {
                call.respond("short")
            }
            get("/plain/path/with/multiple/components") {
                call.respond("long")
            }
            get("/plain/{path}/with/parameters/components") {
                call.respond("param ${call.parameters["path"] ?: "Fail"}")
            }
        }
    }

    @Setup
    fun startServer() = runBlocking {
        testHost.start()
    }

    @TearDown
    fun stopServer() {
        testHost.stop()
    }

    @Benchmark
    fun shortPath() = handle("/short") {
        check(body<String>() == "short") { "Invalid response" }
    }

    @Benchmark
    fun longPath() = handle("/plain/path/with/multiple/components") {
        check(body<String>() == "long") { "Invalid response" }
    }

    @Benchmark
    fun paramPath() = handle("/plain/OK/with/parameters/components") {
        check(body<String>() == "param OK") { "Invalid response" }
    }

    private inline fun <R> handle(url: String, crossinline block: suspend HttpResponse.() -> R) = runBlocking {
        testHost.client.get(url).let { response ->
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("wrong response code")
            }

            response.block()
        }
    }
}

/*
Benchmark                    Mode  Cnt     Score    Error   Units
RoutingBenchmark.longPath   thrpt   20   872.538 ± 26.187  ops/ms
RoutingBenchmark.paramPath  thrpt   20   814.574 ± 15.689  ops/ms
RoutingBenchmark.shortPath  thrpt   20  1022.062 ± 18.937  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<RoutingBenchmark>()
    }
}
