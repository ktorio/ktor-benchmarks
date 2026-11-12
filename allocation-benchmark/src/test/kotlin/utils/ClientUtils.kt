package benchmarks

import benchmarks.utils.trustAllSslContext
import java.net.*
import java.net.http.*

const val SERVER_PORT = 8080

val client: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .build()

val http2Client: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .sslContext(trustAllSslContext)
    .build()

fun createRequest(path: String) = HttpRequest.newBuilder()
    .uri(URI.create("http://127.0.0.1:$SERVER_PORT").resolve(path))
    .build()

fun createHttp2Request(path: String) = HttpRequest.newBuilder()
    .uri(URI.create("https://127.0.0.1:$HTTP2_SERVER_PORT").resolve(path))
    .build()

fun makeRequest(request: HttpRequest) {
    client.send(request, HttpResponse.BodyHandlers.ofString())
}

fun makeHttp2Request(request: HttpRequest) {
    val response = http2Client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.version() == HttpClient.Version.HTTP_2) {
        "Expected an HTTP/2 response, but got ${response.version()}"
    }
}
