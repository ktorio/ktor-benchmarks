# Utils Benchmarks

JMH benchmarks for Ktor utility components, focusing on pipeline execution performance.

## Overview

This module benchmarks core Ktor utility performance, particularly the HTTP pipeline which is central to request processing in Ktor applications.

## Benchmarks

### Pipeline Benchmarks

Tests the performance of Ktor's HTTP pipeline execution:

- `testPipelineExecute` - Standard pipeline execution
- `testDebugPipelineExecute` - Pipeline execution with debug mode
- `testExperimentalPipelineExecute` - Experimental pipeline implementation

## Running Benchmarks

```bash
./gradlew jmh
```

## Configuration

JMH settings in `build.gradle.kts`:

```kotlin
jmh {
    benchmarkMode.set(listOf("thrpt"))  // Throughput mode
    fork.set(1)
    iterations.set(10)
    timeOnIteration.set("1s")
    warmupIterations.set(5)
    warmup.set("1s")
    profilers.set(listOf("gc"))  // GC profiling enabled
    timeUnit.set("ms")
}
```

## Latest Results

```
PipelineBenchmark.testDebugPipelineExecute         avgt   10  0.007 ±  0.001  ms/op
PipelineBenchmark.testPipelineExecute              avgt   10  0.033 ±  0.001  ms/op
```

## Key Findings

- Debug pipeline is significantly faster (~0.007ms vs ~0.033ms)
- Pipeline execution overhead is minimal (microsecond range)
- Debug mode has ~4.7x better performance in this microbenchmark

## Additional Testing

The module also includes allocation tests:

- `PipelineAllocationTest` - Measures memory allocations during pipeline execution

Run with:
```bash
./gradlew test
```

## Dependencies

- ktor-utils - Ktor utility functions
- ktor-io - Ktor I/O primitives
- ktor-network - Ktor network utilities
- kotlinx-serialization-json - JSON serialization
- instrumenter - Memory allocation instrumentation

## Profiling Options

The build file includes commented profiler configurations:

```kotlin
// Async profiler for CPU/allocation profiling
// profilers.set(listOf("async:libPath=/path/to/async-profiler/libasyncProfiler.so"))

// GC profiler (currently enabled)
profilers.set(listOf("gc"))

// Performance assembly profiler
// profilers.set(listOf("perfasm"))
```

## Use Cases

- Measure pipeline execution overhead
- Compare pipeline implementations
- Identify performance regressions in core utilities
- Understand allocation patterns in pipeline execution