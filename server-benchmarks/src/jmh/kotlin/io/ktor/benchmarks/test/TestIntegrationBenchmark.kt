/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.benchmarks.test

import io.ktor.server.application.*
import io.ktor.benchmarks.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking

class TestIntegrationBenchmark : IntegrationBenchmark<TestApplicationEngine>() {

    override val localhost: String = ""
    private val testApplication = TestApplication {}

    override fun createServer(port: Int, main: Application.() -> Unit): EmbeddedServer<TestApplicationEngine, *> {
        return embeddedServer(TestEngine, port, module = main)
    }

    override fun load(url: String) {
        runBlocking {
            testApplication.client.get(url).let { response ->
                if (response.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Expected 'HttpStatusCode.OK' but got '${response.status}'")
                }
                response.body<ByteArray>()
            }
        }
    }
}
