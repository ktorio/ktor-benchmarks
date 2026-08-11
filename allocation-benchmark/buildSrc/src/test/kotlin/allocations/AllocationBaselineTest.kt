package allocations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AllocationBaselineTest {
    @Test
    fun `selects baseline from TeamCity pull request target branch`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(
                explicitBaseline = null,
                teamCityProperties = TeamCityProperties("refs/heads/release/3.x", "pull/123"),
                ktorVersion = "1.0.0-BENCHMARKS",
            ),
        )
        assertEquals(
            "release-4.x",
            resolveAllocationBaseline(
                explicitBaseline = null,
                teamCityProperties = TeamCityProperties("release/4.x", "pull/123"),
                ktorVersion = "1.0.0-BENCHMARKS",
            ),
        )
        assertEquals(
            "main",
            resolveAllocationBaseline(
                explicitBaseline = null,
                teamCityProperties = TeamCityProperties("main", "pull/123"),
                ktorVersion = "1.0.0-BENCHMARKS",
            ),
        )
    }

    @Test
    fun `selects baseline from direct TeamCity build branch`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(
                explicitBaseline = null,
                teamCityProperties = TeamCityProperties(null, "release/3.x"),
                ktorVersion = "1.0.0-BENCHMARKS",
            ),
        )
        assertEquals(
            "main",
            resolveAllocationBaseline(
                explicitBaseline = null,
                teamCityProperties = TeamCityProperties(null, "main"),
                ktorVersion = "1.0.0-BENCHMARKS",
            ),
        )
    }

    @Test
    fun `selects release baseline for local patch version`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(null, null, "3.5.2"),
        )
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(null, null, "3.5.3-SNAPSHOT"),
        )
        assertEquals(
            "release-4.x",
            resolveAllocationBaseline(null, null, "4.1.1"),
        )
    }

    @Test
    fun `requires explicit baseline for local zero patch version`() {
        assertFailsWith<IllegalStateException> {
            resolveAllocationBaseline(null, null, "3.6.0-SNAPSHOT")
        }
    }

    @Test
    fun `explicit baseline overrides automatic selection`() {
        assertEquals(
            "main",
            resolveAllocationBaseline(
                explicitBaseline = "main",
                teamCityProperties = TeamCityProperties("release/3.x", "release/3.x"),
                ktorVersion = "3.5.2",
            ),
        )
    }

    @Test
    fun `normalizes explicit release branch`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline("release/3.x", null, "3.5.2"),
        )
        assertEquals(
            "release-4.x",
            resolveAllocationBaseline("refs/heads/release/4.x", null, "3.5.2"),
        )
    }

    @Test
    fun `rejects unsupported baseline`() {
        assertFailsWith<IllegalArgumentException> {
            resolveAllocationBaseline("unknown", null, "3.5.2")
        }
    }
}
