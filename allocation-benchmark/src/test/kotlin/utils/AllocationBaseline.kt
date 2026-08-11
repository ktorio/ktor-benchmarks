package utils.benchmarks

import java.io.File

private const val ALLOCATION_BASELINE_PROPERTY = "allocation.baseline"

internal val allocationBaseline: String by lazy {
    checkNotNull(System.getProperty(ALLOCATION_BASELINE_PROPERTY)) {
        "The allocation baseline was not provided by Gradle"
    }
}

internal val allocationBaselineDirectory: File
    get() = File("allocations", allocationBaseline)
