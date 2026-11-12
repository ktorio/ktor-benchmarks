/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.net.InetAddress

private const val KEY_ALIAS = "benchmarks"
private const val KEY_PASSWORD = "benchmarks"

fun main() {
    startServer(port = 8080, wait = true)
}

fun startServer(port: Int, wait: Boolean): EmbeddedServer<*, *> {
    return embeddedServer(CIO, port, module = Application::benchmarks).apply {
        start(wait)
    }
}

/**
 * Starts a Netty server serving the same routes over HTTP/2 with TLS (ALPN `h2`),
 * using a self-signed certificate issued for `localhost` and `127.0.0.1`.
 */
fun startHttp2Server(port: Int, wait: Boolean): EmbeddedServer<*, *> {
    val keyStore = buildKeyStore {
        certificate(KEY_ALIAS) {
            password = KEY_PASSWORD
            domains = listOf("localhost")
            ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
        }
    }

    return embeddedServer(
        Netty,
        configure = {
            enableHttp2 = true
            sslConnector(
                keyStore = keyStore,
                keyAlias = KEY_ALIAS,
                keyStorePassword = { KEY_PASSWORD.toCharArray() },
                privateKeyPassword = { KEY_PASSWORD.toCharArray() },
            ) {
                this.port = port
            }
        },
        module = Application::benchmarks,
    ).apply {
        start(wait)
    }
}
