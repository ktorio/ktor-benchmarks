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
- Plain text responses
- JSON responses

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

## How It Works

1. The Java agent (`instrumenter`) intercepts all object allocations during test execution
2. Tests perform multiple warmup requests, then measure allocations over 300 requests
3. Results are compared against baseline JSON files in the `allocations/` directory
4. A measurement that exceeds the configured tolerance is retried with a fresh engine and coroutine context, up to two times
5. Retries stop as soon as a measurement is within tolerance
6. The lowest complete snapshot is used, and the test fails if all attempts exceed the tolerance

Baseline generation always performs three measurements and stores the lowest complete snapshot. This lower-envelope approach filters incidental pool misses and scheduler interference without merging data from different attempts. Retried tests print every attempt total and the observed spread.

## Baseline Management

### Version tags

Each released Ktor version has a corresponding git tag in this repository using the same `vX.Y.Z` format as the Ktor repo (e.g. `v3.4.1`).
Tags point to the commit that committed the allocation baseline for that release.
Use them to compare baselines between versions.

### Baseline files

Baselines are stored as JSON files in `allocations/`:
- `helloWorld[EngineName].json` - Server hello world endpoint allocations
- `fileResponse[EngineName].json` - Server file response allocations
- `plainText[EngineName].json` - Client plain text request allocations
- `json[EngineName].json` - Client JSON request allocations

`allocations/tolerances.json` defines the default allowed increase and bounded, report-specific known variances. Known variances must include a reason so tests and analysis tools can distinguish documented measurement artifacts from regressions. Raw allocation dumps and diffs are never adjusted.

**When to update baselines:**
- After intentional changes that affect memory usage
- When CI consistently fails with allocation differences
- After Kotlin/Ktor version upgrades that change allocation patterns

**How to update baselines:**
1. Run `./gradlew dumpAllocations`; every scenario is measured three times
2. Review the selected lowest snapshot and the attempt spread printed by the test
3. Review the changes in `allocations/` directory
4. Commit the new baselines if changes are expected

## Configuration

Key parameters in tests:
- `TEST_SIZE = 300L` - Number of requests measured per test
- `WARMUP_SIZE = 20` - Number of warmup requests before measurement
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
- The instrumenter dependency is not configured correctly
- Run `./gradlew clean build` to refresh dependencies
