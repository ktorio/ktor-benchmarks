# Ktor Benchmarks

This repository contains benchmark tests for various performance metrics.

### Allocation Benchmark (/allocation-benchmark)

Tests how much memory is allocated on a given request.  This is done before every merge to ensure that the ktor server
maintains a low memory profile by avoiding erroneous allocations.

**Testing:** `ServerCallAllocationTest` runs through the supported engines and calculates the memory allocated per request.  When a new baseline is required,
you may run the `dumpAllocations` gradle target.

**Inspection:** the allocation baseline dump is a series of JSON files.  For easy reading of the values exported here, use the `reportServer` gradle target
which has two pages for inspecting where memory is being allocated.  `previewClasses.html` shows the largest memory consumers for types allocated, and
`previewSites.html` shows the code sites that allocate the most memory.

**TeamCity:** [https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests](https://ktor.teamcity.com/buildConfiguration/Ktor_AllocationTests)
