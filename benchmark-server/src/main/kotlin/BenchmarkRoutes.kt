/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*

internal fun Application.benchmarks() {
    install(WebSockets)

    routing {
        staticResources("", basePackage = "benchmarks")

        route("/benchmarks") {
            val oneMegabyte = makeArray(1 * MB)
            val testData = "{'message': 'Hello World'}"

            suspend fun RoutingCall.respondBytes(size: Int) {
                val megabytes = size / MB
                val bytes = size % MB

                respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            repeat(megabytes) { channel.writeFully(oneMegabyte) }
                            if (bytes > 0) channel.writeFully(oneMegabyte, 0, bytes)
                        }
                    }
                )
            }

            /** Simple text response. */
            get("/hello") {
                call.respondText("Hello, World!")
            }

            /**
             * Receive json data-class.
             */
            get("/json") {
                call.respondText(testData, ContentType.Application.Json)
            }

            /**
             * Send json data-class.
             */
            post("/json") {
                val request = call.receiveText()
                check(testData == request)
                call.respond(HttpStatusCode.OK, "OK")
            }

            /**
             * Submit url form.
             */
            get("/form-url") {
            }

            /**
             * Submit body form.
             */
            post("/form-body") {
            }

            /**
             * Download file.
             */
            get("/bytes") {
                val size = call.request.queryParameters["size"]!!.toInt()
                call.respondBytes(size)
            }

            /**
             * Upload file.
             */
            post("/bytes") {
                val content = call.receive<ByteArray>()
                call.respond("${content.size}")
            }

            /**
             * Upload file.
             */
            post("/echo") {
                val channel = call.request.receiveChannel()
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom(): ByteReadChannel = channel
                    }
                )
            }

            route("/gzip") {
                install(Compression) {
                    gzip()
                }
                get {
                    val size = call.request.queryParameters["size"]!!.toInt()
                    call.respondBytes(size)
                }
            }

            route("/websockets") {
                webSocket("/get/{count}") {
                    val count = call.parameters["count"]!!.toInt()

                    repeat(count) {
                        send("$it")
                    }
                }
            }
        }
    }
}

private const val MB = 1024 * 1024
