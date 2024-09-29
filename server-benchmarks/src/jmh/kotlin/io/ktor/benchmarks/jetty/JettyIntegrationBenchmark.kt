/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.benchmarks.jetty

import io.ktor.server.application.*
import io.ktor.benchmarks.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*

class JettyIntegrationBenchmark : IntegrationBenchmark<JettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): EmbeddedServer<JettyApplicationEngine, *> {
        return embeddedServer(Jetty, port, module = main)
    }
}

class JettyAsyncIntegrationBenchmark : AsyncIntegrationBenchmark<JettyApplicationEngine>() {
    override fun createServer(port: Int, main: Application.() -> Unit): EmbeddedServer<JettyApplicationEngine, *> {
        return embeddedServer(Jetty, port, module = main)
    }
}
