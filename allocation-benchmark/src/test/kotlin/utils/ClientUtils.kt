package benchmarks

import java.net.*
import java.net.http.*

const val SERVER_PORT = 8080

val client = HttpClient.newHttpClient()

fun createRequest(path: String) = HttpRequest.newBuilder()
    .uri(URI.create("http://127.0.0.1:$SERVER_PORT").resolve(path))
    .build()

fun makeRequest(request: HttpRequest) {
    client.send(request, HttpResponse.BodyHandlers.ofString())
}
