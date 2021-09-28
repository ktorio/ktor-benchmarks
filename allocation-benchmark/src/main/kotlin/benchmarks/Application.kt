package benchmarks

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.tomcat.*
import java.util.*

fun Application.main() {
    routing {
        static {
            defaultResource("index.html")
        }
        get("clear") {
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun server(engineName: String): BaseApplicationEngine {
    val engine = when (engineName.lowercase(Locale.getDefault())) {
        "netty" -> Netty
        "jetty" -> Jetty
        "cio" -> CIO
        "tomcat" -> Tomcat
        else -> error("Unknown engine provided: $engineName")
    }

    val environment = applicationEngineEnvironment {
        connector {
            host = "0.0.0.0"
            port = 8080
        }

        developmentMode = false
        module(Application::main)
    }

    val server = embeddedServer(engine, environment)
    server.start()
    return server
}
