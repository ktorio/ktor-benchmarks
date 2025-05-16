/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    startServer(port = 8080, wait = true)
}

fun startServer(port: Int, wait: Boolean): EmbeddedServer<*, *> {
    return embeddedServer(CIO, port, module = Application::benchmarks).apply {
        start(wait)
    }
}
