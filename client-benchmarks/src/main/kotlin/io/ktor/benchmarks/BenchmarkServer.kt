// ABOUTME: Embedded Netty server for client benchmarks
// ABOUTME: Provides simple endpoints for testing client performance

package io.ktor.benchmarks

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun createBenchmarkServer(port: Int): EmbeddedServer<*, *> {
    return embeddedServer(Netty, port = port) {
        routing {
            get("/health") {
                call.respondText("OK")
            }
            get("/benchmarks/hello") {
                call.respondText("Hello, World!")
            }
            get("/benchmarks/bytes") {
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 1024
                call.respondBytes(ByteArray(size) { it.toByte() })
            }
        }
    }
}
