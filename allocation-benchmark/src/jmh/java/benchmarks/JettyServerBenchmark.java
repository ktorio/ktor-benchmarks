package benchmarks;

import io.ktor.server.engine.EmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(10)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
public class JettyServerBenchmark {
    private EmbeddedServer<?, ?> server;
    private HttpClient client;
    private HttpRequest request;

    @Setup(Level.Trial)
    public void setUp() {
        server = ApplicationKt.server("Jetty");
        client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/"))
            .build();

        HttpResponse<Void> response = executeRequest();
        if (response.version() != HttpClient.Version.HTTP_2) {
            throw new IllegalStateException("Expected HTTP/2, got " + response.version());
        }
        JitStabilizer.await(() -> executeRequest());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // GCProfiler samples after trial teardown. Stop asynchronously so shutdown does not alter the final sample.
        new Thread(() -> {
            LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
            server.stop(1000, 5000);
        }, "jetty-benchmark-shutdown").start();
    }

    @Benchmark
    public int fileResponse() {
        return executeRequest().statusCode();
    }

    private HttpResponse<Void> executeRequest() {
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Unexpected HTTP status " + response.statusCode());
            }
            return response;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(cause);
        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }
}
