/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import io.ktor.websocket.*

internal fun Application.benchmarks() {
    routing {
        route("/benchmarks") {
            val testBytes = makeArray(1024 * 1024)
            val testData = "{'message': 'Hello World'}"

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
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(testBytes, 0, size * 1024)
                        }
                    }
                )
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
