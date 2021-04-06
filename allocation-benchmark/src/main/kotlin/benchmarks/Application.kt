package benchmarks

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

@OptIn(EngineAPI::class)
fun server(engineName: String): BaseApplicationEngine {
    val engine = when (engineName.toLowerCase()) {
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
