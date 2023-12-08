package benchmarks

import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    val port = 8082
    embeddedServer(Netty, port = port) {
        routing {
            staticFiles("/", File("allocations"))
            staticFiles("/test_output", File("build/allocations"))
        }

        println("""
            Classes: http://localhost:$port/previewClasses.html
            Sites:   http://localhost:$port/previewSites.html
        """.trimIndent())
    }.start(wait = true)
}