# Ktor Benchmarks

This repository contains benchmark tests for various Ktor performance metrics including memory allocation, throughput, I/O operations, and utilities.

## Prerequisites

- JDK 17 or higher
- Gradle (wrapper included)

## Project Structure

### [allocation-benchmark](allocation-benchmark/)

Tests memory allocation on a given request to ensure Ktor maintains a low memory profile by avoiding erroneous allocations.

**Tests:**
- `ServerCallAllocationTest` - Measures memory allocated per request for server engines (Jetty, Tomcat, Netty, CIO)
- `ClientCallAllocationTest` - Measures memory allocated per request for client engines (CIO, Apache, OkHttp, Java)

**Running Tests:**
```bash
cd allocation-benchmark
./gradlew test                # Run all allocation tests
./gradlew serverTests         # Run only server allocation tests
./gradlew dumpAllocations     # Generate new allocation baselines
./gradlew reportServer        # Start web server to view allocation reports
```

**Viewing Reports:**
After running `reportServer`, open your browser to view:
- `previewClasses.html` - Largest memory consumers by type
- `previewSites.html` - Code sites that allocate the most memory

**TeamCity:** [Allocation Tests](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests)

### [server-benchmarks](server-benchmarks/)

JMH-based benchmarks for testing Ktor server performance under various scenarios including integration tests and specific feature benchmarks.

**Running Benchmarks:**
```bash
cd server-benchmarks
./gradlew jmh                 # Run benchmarks (configure includes in build.gradle.kts)
```

**Note:** Edit `build.gradle.kts` to configure which benchmarks to run via the `includes` list.

### [client-benchmarks](client-benchmarks/)

Throughput benchmarks comparing different Ktor client engines (CIO, Apache, OkHttp, Java) against a local server.

**Running Benchmarks:**
```bash
cd client-benchmarks
./gradlew run                 # Run client throughput benchmarks
```

This will:
1. Start a Netty server on port 8080
2. Run concurrent requests using different client engines
3. Report requests per second for each engine

### [io-benchmarks](io-benchmarks/)

JMH benchmarks for I/O operations including file reading and socket operations across different dispatchers and implementations.

**Running Benchmarks:**
```bash
cd io-benchmarks
./gradlew jmh                 # Run all I/O benchmarks
```

**Benchmarks include:**
- File reading with various dispatchers (IO, Hot, Fixed, Blocking, etc.)
- Socket read/write operations
- Comparison between JVM native and Ktor implementations

See [io-benchmarks/README.md](io-benchmarks/README.md) for latest results.

### [utils-benchmarks](utils-benchmarks/)

JMH benchmarks for Ktor utility components, primarily focusing on pipeline execution performance.

**Running Benchmarks:**
```bash
cd utils-benchmarks
./gradlew jmh                 # Run pipeline benchmarks
```

See [utils-benchmarks/README.md](utils-benchmarks/README.md) for latest results.

### [benchmark-server](benchmark-server/)

Gradle plugin providing test server infrastructure used by other benchmark modules. Contains reusable server configurations and routes.

## Quick Start

To run all benchmarks:

```bash
# Allocation tests
cd allocation-benchmark && ./gradlew test && cd ..

# Server benchmarks
cd server-benchmarks && ./gradlew jmh && cd ..

# Client benchmarks
cd client-benchmarks && ./gradlew run && cd ..

# I/O benchmarks
cd io-benchmarks && ./gradlew jmh && cd ..

# Utils benchmarks
cd utils-benchmarks && ./gradlew jmh && cd ..
```

## Benchmark Types

- **Allocation Benchmarks** - Memory allocation testing using instrumentation agent
- **JMH Benchmarks** - Standard JMH microbenchmarks for throughput and average time measurements
- **Throughput Benchmarks** - Concurrent request testing for client/server performance

## Configuration

- JVM arguments and benchmark parameters are configured in each subproject's `build.gradle.kts`
- Allocation baselines are stored in `allocations/` directories
- JMH results may be output to CSV files in respective directories
