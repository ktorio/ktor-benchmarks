# IO Benchmarks

JMH benchmarks for Ktor I/O operations including file reading and socket operations across different dispatchers.

## Overview

This module benchmarks Ktor's I/O performance characteristics, comparing:
- Different coroutine dispatchers for file I/O
- Ktor vs JVM native I/O implementations
- Socket read/write performance
- Auto-flush vs manual flush strategies

## Benchmark Categories

### File Benchmarks

Tests reading files using different approaches and dispatchers:

**JVM Native:**
- `testJvmRandomFileRead` - RandomAccessFile
- `testJvmStreamRead` - FileInputStream (single file)
- `testJvmStreamRead100` - FileInputStream (100 files)
- `testFilesReadChannel100` - NIO FileChannel (100 files)

**Ktor with Various Dispatchers:**
- `testKtorFileRead` - Default dispatcher
- `testKtorFileReadUnconfined` - Unconfined dispatcher
- `testKtorFileReadInIODispatcher` - IO dispatcher
- `testKtorFileReadInHotDispatcher` - Hot dispatcher (cached)
- `testKtorFileReadInFixedDispatcher` - Fixed thread pool
- `testKtorFileReadInBlockingDispatcher` - Blocking dispatcher
- `testKtorFileReadInExpDispatcher` - Exponential growth pool
- `testKtorFakeFileRead` - Memory-based fake file

### Socket Benchmarks

Tests socket write operations:

- `testJvmSocketWrite` - Native JVM socket
- `testKtorSocketWrite` - Ktor socket with auto-flush
- `testKtorSocketWriteWithoutAutoFlush` - Ktor socket manual flush

## Running Benchmarks

```bash
./gradlew jmh
```

## Configuration

JMH settings in `build.gradle.kts`:

```kotlin
jmh {
    benchmarkMode.set(listOf("avgt"))  // Average time
    fork.set(1)
    iterations.set(10)
    timeOnIteration.set("5s")
    warmupIterations.set(5)
    warmup.set("1s")
    timeUnit.set("ms")
}
```

## Latest Results

```
Benchmark                                                            Mode  Cnt      Score     Error  Units
FileBenchmarks.testFilesReadChannel100                               avgt   10  12784.632 ± 367.794  ms/op
FileBenchmarks.testJvmRandomFileRead                                 avgt   10     30.879 ±   0.476  ms/op
FileBenchmarks.testJvmStreamRead                                     avgt   10     24.337 ±   0.586  ms/op
FileBenchmarks.testJvmStreamRead100                                  avgt   10   2480.255 ±  27.500  ms/op
FileBenchmarks.testKtorFakeFileRead                                  avgt   10    364.762 ±  25.866  ms/op
FileBenchmarks.testKtorFakeFileReadInHot                             avgt   10     73.430 ±   3.759  ms/op
FileBenchmarks.testKtorFileRead                                      avgt   10    368.891 ±  12.417  ms/op
FileBenchmarks.testKtorFileReadInBlocking2BlockingDispatcher         avgt   10    327.188 ±  25.345  ms/op
FileBenchmarks.testKtorFileReadInExp2ExpDispatcher                   avgt   10    285.341 ±  26.109  ms/op
FileBenchmarks.testKtorFileReadInFixed2FixedDispatcher               avgt   10    335.269 ±  26.333  ms/op
FileBenchmarks.testKtorFileReadInFixedDispatcher                     avgt   10    327.743 ±  17.517  ms/op
FileBenchmarks.testKtorFileReadInHot2HotDispatcher                   avgt   10     69.893 ±   2.384  ms/op
FileBenchmarks.testKtorFileReadInHotDispatcher                       avgt   10    189.024 ±   8.091  ms/op
FileBenchmarks.testKtorFileReadInHugeExp2HugeExpDispatcher           avgt   10    334.480 ±  24.396  ms/op
FileBenchmarks.testKtorFileReadInIODispatcher                        avgt   10     68.801 ±   1.771  ms/op
FileBenchmarks.testKtorFileReadInIo2IoDispatcher                     avgt   10     76.688 ±   4.480  ms/op
FileBenchmarks.testKtorFileReadUnconfined                            avgt   10     64.487 ±   1.205  ms/op
SocketBenchmarks.testJvmSocketWrite                                  avgt   10     41.340 ±   1.515  ms/op
SocketBenchmarks.testKtorSocketWrite                                 avgt   10    138.893 ±   2.345  ms/op
SocketBenchmarks.testKtorSocketWriteWithoutAutoFlush                 avgt   10    154.011 ±  11.539  ms/op
```

## Key Findings

**File Reading:**
- JVM native FileInputStream is fastest for single files (~24ms)
- Ktor with Hot dispatcher performs best (~69-73ms)
- Ktor with IO dispatcher is competitive (~69ms)
- Unconfined dispatcher shows good performance (~64ms)

**Socket Writing:**
- JVM native socket is faster (~41ms)
- Ktor socket with auto-flush has overhead (~139ms)
- Manual flush doesn't improve performance significantly

## Dependencies

- kotlinx-io - Kotlin I/O library
- ktor-io - Ktor I/O primitives
- ktor-network - Ktor network utilities
- ktor-utils - Ktor utility functions

## Use Cases

- Compare I/O approaches for Ktor applications
- Identify optimal dispatcher for file operations
- Understand I/O overhead in different scenarios
- Measure impact of Ktor abstractions vs native I/O