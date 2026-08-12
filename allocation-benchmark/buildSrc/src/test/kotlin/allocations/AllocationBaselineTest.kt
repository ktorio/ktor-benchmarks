package allocations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AllocationBaselineTest {
    @Test
    fun `selects release baseline for local patch version`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(null, "3.5.2"),
        )
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline(null, "3.5.3-SNAPSHOT"),
        )
        assertEquals(
            "release-4.x",
            resolveAllocationBaseline(null, "4.1.1"),
        )
    }

    @Test
    fun `requires explicit baseline for local zero patch version`() {
        assertFailsWith<IllegalStateException> {
            resolveAllocationBaseline(null, "3.6.0-SNAPSHOT")
        }
    }

    @Test
    fun `explicit baseline overrides local version inference`() {
        assertEquals(
            "main",
            resolveAllocationBaseline("main", "3.6.0-BENCHMARKS.1"),
        )
    }

    @Test
    fun `normalizes explicit release branch`() {
        assertEquals(
            "release-3.x",
            resolveAllocationBaseline("release/3.x", "3.5.2"),
        )
        assertEquals(
            "release-4.x",
            resolveAllocationBaseline("refs/heads/release/4.x", "3.5.2"),
        )
    }

    @Test
    fun `rejects unsupported baseline`() {
        assertFailsWith<IllegalArgumentException> {
            resolveAllocationBaseline("unknown", "3.5.2")
        }
    }
}
