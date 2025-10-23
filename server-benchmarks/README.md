# Server Benchmarks

JMH (Java Microbenchmark Harness) benchmarks for Ktor server performance testing.

## Overview

This module contains comprehensive JMH benchmarks for testing Ktor server components, integration scenarios, and performance characteristics across different server engines.

## Benchmark Categories

### Integration Benchmarks

Full end-to-end server benchmarks with real HTTP clients:

- **IntegrationBenchmark** - Generic integration tests
- **AsyncIntegrationBenchmark** - Async client integration tests
- **CIOIntegrationBenchmark** - CIO engine-specific tests
- **NettyIntegrationBenchmark** - Netty engine-specific tests
- **JettyIntegrationBenchmark** - Jetty engine-specific tests
- **TestIntegrationBenchmark** - TestHost engine tests

### Component Benchmarks

Individual component performance tests:

- **PipelineBenchmark** - HTTP pipeline execution performance
- **RoutingBenchmark** - Routing resolution performance
- **ChannelBenchmarks** - ByteChannel operations
- **CodecsBenchmark** - Encoding/decoding operations
- **StringValuesBenchmark** - Headers and parameters parsing
- **CIOChunkedBenchmark** - Chunked transfer encoding (CIO)

### Platform Benchmarks

Engine-specific platform tests:

- **PlatformBenchmark** - Generic platform tests
- **CIOPlatformBenchmark** - CIO platform tests
- **NettyPlatformBenchmark** - Netty platform tests
- **JettyPlatformBenchmark** - Jetty platform tests

### Other

- **CoroutineCancellationBenchmark** - Coroutine cancellation overhead

## Running Benchmarks

```bash
# Run all configured benchmarks
./gradlew jmh
```

**Note:** By default, only specific benchmarks run. Edit `build.gradle.kts` to configure which benchmarks to run:

```kotlin
jmh {
    // Change this list for different benchmarks
    includes = listOf(
        "io.ktor.benchmarks.cio.CIOIntegrationBenchmark"
    )
}
```

## Configuration

JMH settings in `build.gradle.kts`:

```kotlin
jmh {
    warmupIterations = 2    // Warmup iterations
    fork = 2                // Number of forks
    iterations = 10         // Measurement iterations
    threads = 32            // Thread count
}
```

## Results

Benchmark results are written to:
- Console output
- `jmh-result.csv` - CSV format for analysis

## Benchmark Modes

Different benchmarks use different JMH modes:
- **Throughput** - Operations per time unit
- **AverageTime** - Average time per operation
- **SampleTime** - Sampling time distribution

## HTTP Clients Used

Benchmarks use various HTTP clients to test servers:
- Apache HttpClient
- OkHttp
- Ktor test client

## Common Benchmark Scenarios

1. **Plain text response** - Minimal overhead baseline
2. **Chunked transfer** - Streaming response performance
3. **Large payloads** - Performance under load
4. **Concurrent requests** - Multi-threaded throughput

## Customizing Benchmarks

To run specific benchmarks:

1. Edit `build.gradle.kts`
2. Update the `includes` list with desired benchmark classes
3. Run `./gradlew jmh`

Example:
```kotlin
includes = listOf(
    "io.ktor.benchmarks.IntegrationBenchmark",
    "io.ktor.benchmarks.RoutingBenchmark"
)
```

To run all benchmarks (warning: very time-consuming):
```kotlin
includes = listOf()  // Empty list = run all
```

## Interpreting Results

- Higher throughput (ops/s) is better
- Lower average time (ms/op) is better
- Check error margins (±) for result stability
- Compare across engines and configurations

## Requirements

- Significant CPU and memory resources
- Multiple runs recommended for consistent results
- Quiet system (minimal background processes)
