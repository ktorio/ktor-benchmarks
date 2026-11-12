/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.engine.*
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.registerIfAbsent

abstract class TestServerService : BuildService<TestServerService.Parameters>, AutoCloseable {

    private val logger = Logging.getLogger("TestServerService")
    private val server: EmbeddedServer<*, *>
    private val http2Server: EmbeddedServer<*, *>

    init {
        logger.lifecycle("Starting test server...")
        server = startServer(parameters.port.get(), wait = false)
        http2Server = startHttp2Server(parameters.http2Port.get(), wait = false)
        logger.lifecycle("Test server started.")
    }

    override fun close() {
        logger.lifecycle("Stopping test server...")
        server.stop()
        http2Server.stop()
        logger.lifecycle("Test server stopped.")
    }

    interface Parameters : BuildServiceParameters {
        val port: Property<Int>
        val http2Port: Property<Int>
    }

    internal companion object {
        fun registerIfAbsent(project: Project, port: Int, http2Port: Int): Provider<TestServerService> {
            return project.gradle.sharedServices.registerIfAbsent("testServer", TestServerService::class) {
                parameters.port.set(port)
                parameters.http2Port.set(http2Port)
            }
        }
    }
}