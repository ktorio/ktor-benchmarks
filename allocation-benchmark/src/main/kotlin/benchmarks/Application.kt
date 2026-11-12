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
import org.apache.catalina.Lifecycle
import org.apache.catalina.LifecycleListener
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.http11.AbstractHttp11Protocol
import org.apache.coyote.http2.Http2Protocol
import java.util.*

const val HTTP2_SERVER_PORT = 8443

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

/**
 * Starts a server for [engineName] serving the same routes over HTTP/2 with TLS (ALPN `h2`)
 * on [HTTP2_SERVER_PORT].
 *
 * CIO is not supported, as it has neither TLS nor HTTP/2 support on the server side.
 */
fun http2Server(engineName: String): EmbeddedServer<*, *> {
    val server = when (engineName.lowercase(Locale.getDefault())) {
        "netty" -> embeddedServer(Netty, configure = {
            enableHttp2 = true
            tlsConnector(HTTP2_SERVER_PORT)
        }) { main() }

        "jetty" -> embeddedServer(Jetty, configure = {
            tlsConnector(HTTP2_SERVER_PORT)
        }) { main() }

        "tomcat" -> embeddedServer(Tomcat, configure = {
            tlsConnector(HTTP2_SERVER_PORT)
            configureTomcat = { enableHttp2OnSslConnectors() }
        }) { main() }

        else -> error("HTTP/2 is not supported for engine: $engineName")
    }
    server.start()
    return server
}

/**
 * Ktor registers Tomcat's [Http2Protocol] only when the native OpenSSL implementation is available,
 * so it is added to the TLS connectors here to get HTTP/2 over JSSE (ALPN).
 * Connectors are created after `configureTomcat` runs, so this is done right before the server is initialized.
 */
private fun Tomcat.enableHttp2OnSslConnectors() {
    server.addLifecycleListener(LifecycleListener { event ->
        if (event.type != Lifecycle.BEFORE_INIT_EVENT) return@LifecycleListener
        for (connector in service.findConnectors()) {
            val isSsl = (connector.protocolHandler as? AbstractHttp11Protocol<*>)?.isSSLEnabled == true
            if (isSsl && connector.findUpgradeProtocols().none { it is Http2Protocol }) {
                connector.addUpgradeProtocol(Http2Protocol())
            }
        }
    })
}
