package com.example

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.tomcat.*
import kotlinx.coroutines.*

fun Application.main() {
    routing {
        static {
            defaultResource("index.html")
        }
        get("stats") {
            call.respondText {
                AllocationSampler.stats()
            }
        }
        get("clear") {
            call.respond(HttpStatusCode.OK)
        }
    }
    install(ShutDownUrl.ApplicationCallFeature) {
        shutDownUrl = "/shutdown"
    }
}

@OptIn(EngineAPI::class)
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        error("Engine should be provided")
    }

    val engine = when (val engineName = args.first().toLowerCase()) {
        "netty" -> Netty
        "jetty" -> Jetty
        "cio" -> CIO
        "tomcat" -> Tomcat
        else -> error("Unknown engine provided: $engineName")
    }

    val server = embeddedServer(engine, port = 8080, module = Application::main)
    server.start(wait = true)
}
