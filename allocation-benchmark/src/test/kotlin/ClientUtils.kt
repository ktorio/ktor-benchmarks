package benchmarks

import java.net.*
import java.net.http.*


const val SERVER_PORT = 8080

val client = HttpClient.newHttpClient()
val request = HttpRequest.newBuilder()
    .uri(URI.create("http://127.0.0.1:$SERVER_PORT"))
    .build()

fun makeRequest() {
    client.send(request, HttpResponse.BodyHandlers.ofString())
}