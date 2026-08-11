package utils.benchmarks

import kotlin.test.Test
import kotlin.test.assertTrue

class AllocationBaselineTest {
    @Test
    fun `Gradle provides an existing allocation baseline`() {
        assertTrue(
            allocationBaseline == "main" || Regex("^release-\\d+\\.x$").matches(allocationBaseline),
            "Unexpected allocation baseline: $allocationBaseline",
        )
        assertTrue(
            allocationBaselineDirectory.isDirectory,
            "Allocation baseline directory does not exist: $allocationBaselineDirectory",
        )
    }
}
