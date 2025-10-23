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
4. Tests fail if allocations deviate more than 12% from baseline

## Baseline Management

Baselines are stored as JSON files in `allocations/`:
- `helloWorld[EngineName].json` - Server hello world endpoint allocations
- `fileResponse[EngineName].json` - Server file response allocations
- `plainText[EngineName].json` - Client plain text request allocations
- `json[EngineName].json` - Client JSON request allocations

**When to update baselines:**
- After intentional changes that affect memory usage
- When CI consistently fails with allocation differences
- After Kotlin/Ktor version upgrades that change allocation patterns

**How to update baselines:**
1. Run `./gradlew dumpAllocations`
2. Review the changes in `allocations/` directory
3. Commit the new baselines if changes are expected

## Configuration

Key parameters in tests:
- `TEST_SIZE = 300L` - Number of requests measured per test
- `WARMUP_SIZE = 20` - Number of warmup requests before measurement
- `ALLOWED_MEMORY_DIFFERENCE_RATIO = 0.12` - 12% tolerance for allocation differences

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
