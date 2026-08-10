package io.ktor.benchmarks

import io.ktor.http.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
class HttpEncodingBenchmarks {
    private lateinit var pathSegments: Array<String>
    private lateinit var queryComponents: Array<String>
    private lateinit var headerNames: Array<String>

    @Setup
    fun setup() {
        pathSegments = arrayOf(
            "api",
            "users",
            "v2",
            "550e8400-e29b-41d4-a716-446655440000",
            "orders-2026-08",
            "file name.txt",
            "hello world",
            "café-menu",
        )

        queryComponents = arrayOf(
            "kotlin coroutines guide",
            "user@example.com",
            "price>100&x=1",
            "plain",
            "a b+c",
            "München weather",
        )

        headerNames = arrayOf(
            "Content-Type",
            "Content-Length",
            "Authorization",
            "X-Request-Id",
            "Accept-Encoding",
            "User-Agent",
            "Cache-Control",
            "X-Correlation-Id",
        )
    }

    @Benchmark
    fun testEncodeURLPathPart(): Int {
        var length = 0
        for (segment in pathSegments) {
            length += segment.encodeURLPathPart().length
        }
        return length
    }

    @Benchmark
    fun testEncodeURLQueryComponent(): Int {
        var length = 0
        for (component in queryComponents) {
            length += component.encodeURLQueryComponent().length
        }
        return length
    }

    @Benchmark
    fun testEncodeURLParameter(): Int {
        var length = 0
        for (component in queryComponents) {
            length += component.encodeURLParameter(spaceToPlus = true).length
        }
        return length
    }

    @Benchmark
    fun testCheckHeaderName(): Int {
        var checked = 0
        for (name in headerNames) {
            HttpHeaders.checkHeaderName(name)
            checked++
        }
        return checked
    }
}
