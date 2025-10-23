# Client Benchmarks

Throughput benchmarks for Ktor HTTP client engines, measuring concurrent request performance.

## Overview

This benchmark suite tests the throughput (requests per second) of different Ktor client engines by sending concurrent requests to a local Netty server. It helps identify performance characteristics of each client engine under load.

## Client Engines Tested

- **CIO** - Kotlin coroutine-based I/O client
- **Apache** - Apache HttpClient wrapper
- **OkHttp** - OkHttp client wrapper
- **Java** - Java 11+ HTTP client wrapper

## Benchmark Methodology

1. Starts a local Netty server on port 8080
2. Waits for server to be healthy
3. For each client engine:
   - Runs 10 batches of 1000 requests each
   - Uses 100 concurrent coroutines
   - Measures total time and calculates requests per second

## Running Benchmarks

```bash
./gradlew run
```

Sample output:
```
Starting benchmark server on port 8080...
Waiting for server to start...
Server is ready

Running benchmarks...
Batch 1/10 completed...
[...]

=== Benchmark Results ===
CIO: 45,234 requests/sec
Apache: 42,156 requests/sec
OkHttp: 38,921 requests/sec
Java: 40,567 requests/sec
```

## Configuration

Default settings (in `BenchmarkConfig`):
- `batchCount = 10` - Number of batches to run
- `batchSize = 1000` - Requests per batch
- `threadCount = 100` - Concurrent coroutines
- `port = 8080` - Server port

To modify settings, edit `src/main/kotlin/io/ktor/benchmarks/ClientBenchmark.kt`.

## Benchmarked Endpoint

Tests use the `/hello` endpoint which returns a plain text "Hello, World!" response. This isolates client performance from server processing time.

## Use Cases

- Compare client engine performance
- Identify regression in client throughput
- Measure impact of client configuration changes
- Understand concurrency characteristics of different engines

## Notes

- Results vary based on hardware and system load
- Run multiple times for consistent results
- Close other applications for more accurate measurements
- Server runs on the same machine, so network latency is minimal
