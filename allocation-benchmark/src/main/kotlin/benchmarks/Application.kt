package benchmarks

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.tomcat.jakarta.*
import java.util.*

fun Application.main() {
    routing {
        staticResources("", basePackage = "benchmarks")

        get("/hello") {
            call.respondText("Hello, World!")
        }

        get("clear") {
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun server(engineName: String): EmbeddedServer<*, *> {
    val engine = when (engineName.lowercase(Locale.getDefault())) {
        "netty" -> Netty
        "jetty" -> Jetty
        "cio" -> CIO
        "tomcat" -> Tomcat
        else -> error("Unknown engine provided: $engineName")
    }

    val server = embeddedServer(engine, host = "0.0.0.0", port = 8080) {
        main()
    }
    server.start()
    return server
}
