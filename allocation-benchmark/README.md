# Allocation Benchmark

Memory allocation benchmarks for Ktor server and client engines. These tests ensure Ktor maintains a low memory footprint by tracking and validating memory allocations per request.

## Overview

This benchmark suite uses a Java instrumentation agent to measure exact memory allocations during HTTP request/response cycles. Results are compared against baseline allocations to detect regressions.

## Test Suites

### ServerCallAllocationTest

Tests memory allocations for Ktor server engines processing requests.

**Engines tested:**
- Jetty
- Tomcat
- Netty
- CIO

**Endpoints tested:**
- `/hello` - Simple "Hello World" response
- `/` - File response

### ClientCallAllocationTest

Tests memory allocations for Ktor client engines making requests.

**Engines tested:**
- CIO
- Apache
- OkHttp
- Java

**Test scenarios:**
- Small text response
- File response
- 2 MB streaming response
- 2 MB gzip response

## Running Tests

```bash
# Run all allocation tests
./gradlew test

# Run only server allocation tests
./gradlew serverTests

# Generate new allocation baselines (when intentional changes are made)
./gradlew dumpAllocations
```

## Viewing Allocation Reports

The benchmark generates detailed allocation reports showing where memory is being allocated.

```bash
# Start web server to view reports
./gradlew reportServer
```

Then open your browser to the displayed URL. The report server provides two views:

- **previewClasses.html** - Shows the largest memory consumers by allocated type
- **previewSites.html** - Shows the code sites (stack traces) that allocate the most memory

Both views support server and client benchmarks and can display the Main, Release 3.x, or Local snapshot. Local reports are read from `build/allocations/` and are produced by regular test runs.

## How It Works

1. The Java agent (`instrumenter`) intercepts all object allocations during test execution
2. Tests perform multiple warmup requests, then measure allocations over 300 requests
3. Results are compared against branch-specific baseline JSON files in `allocations/main/` or `allocations/release-MAJOR.x/`
4. A measurement that exceeds the default tolerance is retried with a fresh engine and coroutine context, up to two times
5. Retries stop as soon as a measurement is within the default tolerance
6. The lowest complete snapshot is evaluated using the full tolerance policy, including configured known variances

Baseline generation always performs three measurements and stores the lowest complete snapshot. This lower-envelope approach filters incidental pool misses and scheduler interference without merging data from different attempts. Retried tests print every attempt total and the observed spread.

## Baseline Management

### Version tags

Each released Ktor version has a corresponding git tag in this repository using the same `vX.Y.Z` format as the Ktor repo (e.g. `v3.4.1`).
Tags point to the commit that committed the allocation baseline for that release.
Use them to compare baselines between versions.

### Baseline files

Baselines are stored in branch-specific directories: `allocations/<branch-name>/`

Each directory contains files such as:

- `helloWorld[EngineName].json` — server hello world endpoint allocations
- `fileResponse[EngineName].json` — server file response allocations
- `client/helloWorld[EngineName].json` — client small-response allocations

CI selects the baseline explicitly. Locally, a Ktor version with a non-zero patch component selects the release baseline derived from its major version. A zero patch component is ambiguous and requires an explicit baseline. Explicit selection always takes precedence. Release branch names such as `release/3.x` are normalized to baseline directory names such as `release-3.x`:

```bash
./gradlew test -PallocationBaseline=main
./gradlew test -PallocationBaseline=release/3.x
./gradlew dumpAllocations -PallocationBaseline=release/3.x
```

`allocations/tolerances.json` is shared by both baselines and defines the default allowed increase and bounded, report-specific known variances. Each known variance must include a reason, which is shown when the allowance is used. Raw allocation dumps and diffs are never adjusted.

[`known-variations.md`](known-variations.md) documents recurring measurement artifacts and the allocation call-site patterns required to identify them. A matching total or source-file name alone is not sufficient.

**When to update baselines:**
- After intentional changes that affect memory usage
- When CI consistently fails with allocation differences
- After Kotlin/Ktor version upgrades that change allocation patterns

**How to update baselines:**
1. Select the target baseline explicitly with `-PallocationBaseline=main` or `-PallocationBaseline=release/MAJOR.x` when automatic selection would be ambiguous
2. Run `./gradlew dumpAllocations`; every scenario is measured three times
3. Review the selected lowest snapshot and the attempt spread printed by the test
4. Review changes only in the selected baseline directory
5. Commit the new baselines if changes are expected

## Configuration

Key parameters in tests:
- `TEST_SIZE = 300L` - Number of requests measured per test
- JIT warmup runs untracked requests in batches of 50 and checks for stabilization after 300 requests. After compilation time remains unchanged for three consecutive batches, 50 tracked requests warm the allocation sampler before its data is discarded and measurement starts. The test fails if JIT stabilization does not happen within 60 seconds.
- `allocationBaseline` — Gradle property selecting `main` or `release/MAJOR.x`; CI sets it explicitly and local builds may infer it from the Ktor version
- `allocations/tolerances.json` - Default increase tolerance and report/location-specific known variance metadata

## TeamCity Integration

Automated tests run on every PR:
[Ktor Allocation Tests](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests)

## Troubleshooting

**Test fails with allocation difference:**
1. Check if the change is expected (e.g., new feature, dependency update)
2. View the allocation report to identify what changed: `./gradlew reportServer`
3. If acceptable, update baseline: `./gradlew dumpAllocations`

**"Instrumentation agent is not found" error:**
- Verify that the `instrumenter` configuration resolves exactly one agent JAR
- Run `./gradlew dependencies --configuration instrumenter --refresh-dependencies`; if resolution still fails, check the instrumenter repository and dependency configuration
