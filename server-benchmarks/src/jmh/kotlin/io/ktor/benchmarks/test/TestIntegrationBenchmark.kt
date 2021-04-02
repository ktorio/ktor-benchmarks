/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.benchmarks.test

import io.ktor.application.*
import io.ktor.benchmarks.*
import io.ktor.http.*
import io.ktor.server.benchmarks.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*

class TestIntegrationBenchmark : IntegrationBenchmark<TestApplicationEngine>() {

    override val localhost: String = ""

    override fun createServer(port: Int, main: Application.() -> Unit): TestApplicationEngine {
        return embeddedServer(TestEngine, port, module = main)
    }

    override fun load(url: String) {
        server.handleRequest(HttpMethod.Get, url).apply {
            if (response.status() != HttpStatusCode.OK) {
                throw IllegalStateException("Expected 'HttpStatusCode.OK' but got '${response.status()}'")
            }
            response.byteContent!!
        }
    }
}
