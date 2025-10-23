/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.benchmarks.jetty

import io.ktor.benchmarks.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.*

class JettyPlatformBenchmark : PlatformBenchmark() {
    lateinit var server: Server

    override fun runServer(port: Int) {
        server = Server(port)
        val connector = server.getBean(ServerConnector::class.java)
        val config = connector.getBean(HttpConnectionFactory::class.java).httpConfiguration
        config.sendDateHeader = false
        config.sendServerVersion = false

        val pathHandler = PathHandler()
        server.handler = pathHandler

        server.start()
    }

    override fun stopServer() {
        server.stop()
    }

    private class PathHandler : Handler.Abstract() {
        var plainHandler = PlainTextHandler()

        init {
            addBean(plainHandler)
        }

        override fun setServer(server: Server) {
            super.setServer(server)
            plainHandler.server = server
        }

        @Throws(Exception::class)
        override fun handle(
            request: Request,
            response: Response,
            callback: Callback
        ): Boolean {
            val target = request.httpURI.path
            return when (target) {
                "/sayOK" -> plainHandler.handle(request, response, callback)
                else -> false
            }
        }
    }

    private class PlainTextHandler : Handler.Abstract() {
        var helloWorld = BufferUtil.toBuffer("OK")
        var contentType: HttpField =
            PreEncodedHttpField(HttpHeader.CONTENT_TYPE, MimeTypes.Type.TEXT_PLAIN.asString())

        @Throws(Exception::class)
        override fun handle(
            request: Request,
            response: Response,
            callback: Callback
        ): Boolean {
            response.headers.add(contentType)
            response.write(true, helloWorld.slice(), callback)
            return true
        }
    }
}
