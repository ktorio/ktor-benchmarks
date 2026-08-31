package utils.benchmarks

import benchmarks.AllocationData
import benchmarks.LocationInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllocationToleranceTest {
    @Test
    fun `resolves report tolerance from metadata`() {
        val metadata = AllocationToleranceMetadata(
            defaultAllowedIncreaseRatio = 0.01,
            defaultMinimumAllowedIncreaseBytes = 64L,
            reports = mapOf(
                "scenario[Engine]" to ReportTolerance(
                    locations = mapOf(
                        "Source.kt" to KnownLocationVariance(
                            knownVarianceBytes = 1_024L,
                            reason = "Test variance",
                        )
                    )
                )
            ),
        )

        val tolerance = metadata.toleranceFor("scenario[Engine]")

        assertEquals(0.01, tolerance.allowedIncreaseRatio)
        assertEquals(64L, tolerance.minimumAllowedIncreaseBytes)
        assertEquals(1_024L, tolerance.locations.getValue("Source.kt").knownVarianceBytes)
    }

    @Test
    fun `applies minimum allowed increase when ratio allowance is smaller`() {
        val comparison = compareAllocations(
            previous = allocations("Source.kt" to 7_400L),
            current = allocations("Source.kt" to 7_444L),
            tolerance = AllocationTolerance(
                allowedIncreaseRatio = 0.005,
                locations = emptyMap(),
                minimumAllowedIncreaseBytes = 64L,
            ),
        )

        assertEquals(44L, comparison.totalDifference)
        assertEquals(64L, comparison.allowedDifference)
        assertEquals(0L, comparison.unexpectedIncrease)
    }

    @Test
    fun `applies ratio allowance when it is greater than minimum`() {
        val comparison = compareAllocations(
            previous = allocations("Source.kt" to 10_000L),
            current = allocations("Source.kt" to 10_101L),
            tolerance = AllocationTolerance(
                allowedIncreaseRatio = 0.01,
                locations = emptyMap(),
                minimumAllowedIncreaseBytes = 64L,
            ),
        )

        assertEquals(101L, comparison.totalDifference)
        assertEquals(100L, comparison.allowedDifference)
        assertEquals(1L, comparison.unexpectedIncrease)
    }

    @Test
    fun `consumes only configured location increase`() {
        val comparison = compareAllocations(
            previous = allocations("Known.kt" to 100L, "Other.kt" to 100L),
            current = allocations("Known.kt" to 350L, "Other.kt" to 100L),
            tolerance = tolerance(
                allowedIncreaseRatio = 0.0,
                "Known.kt" to 200L,
            ),
        )

        assertEquals(250L, comparison.totalDifference)
        assertEquals(200L, comparison.consumedKnownVariance)
        assertEquals(50L, comparison.adjustedDifference)
        assertEquals(50L, comparison.unexpectedIncrease)
    }

    @Test
    fun `unused location variance does not hide other increase`() {
        val comparison = compareAllocations(
            previous = allocations("Known.kt" to 100L, "Other.kt" to 100L),
            current = allocations("Known.kt" to 100L, "Other.kt" to 150L),
            tolerance = tolerance(
                allowedIncreaseRatio = 0.0,
                "Known.kt" to 200L,
            ),
        )

        assertEquals(0L, comparison.consumedKnownVariance)
        assertEquals(50L, comparison.adjustedDifference)
        assertEquals(50L, comparison.unexpectedIncrease)
    }

    @Test
    fun `does not consume known variance when default tolerance is sufficient`() {
        val comparison = compareAllocations(
            previous = allocations("Known.kt" to 900L, "Other.kt" to 100L),
            current = allocations("Known.kt" to 910L, "Other.kt" to 100L),
            tolerance = tolerance(
                allowedIncreaseRatio = 0.01,
                "Known.kt" to 200L,
            ),
        )

        assertEquals(10L, comparison.totalDifference)
        assertEquals(0L, comparison.consumedKnownVariance)
        assertEquals(10L, comparison.adjustedDifference)
        assertEquals(0L, comparison.unexpectedIncrease)
    }

    @Test
    fun `does not consume known variance for decrease`() {
        val comparison = compareAllocations(
            previous = allocations("Known.kt" to 300L, "Other.kt" to 100L),
            current = allocations("Known.kt" to 100L, "Other.kt" to 150L),
            tolerance = tolerance(
                allowedIncreaseRatio = 0.0,
                "Known.kt" to 200L,
            ),
        )

        assertEquals(0L, comparison.consumedKnownVariance)
        assertEquals(-150L, comparison.adjustedDifference)
        assertEquals(0L, comparison.unexpectedIncrease)
    }

    @Test
    fun `applies known variance when default tolerance is exceeded`() {
        val comparison = compareAllocations(
            previous = allocations("Known.kt" to 900L, "Other.kt" to 100L),
            current = allocations("Known.kt" to 1_100L, "Other.kt" to 110L),
            tolerance = tolerance(
                allowedIncreaseRatio = 0.01,
                "Known.kt" to 200L,
            ),
        )

        assertEquals(210L, comparison.totalDifference)
        assertEquals(200L, comparison.consumedKnownVariance)
        assertEquals(10L, comparison.adjustedDifference)
        assertEquals(10L, comparison.allowedDifference)
        assertEquals(0L, comparison.unexpectedIncrease)
        assertTrue(comparison.exceedsDefaultTolerance)
    }

    @Test
    fun `does not retry successful allocation measurement`() {
        var measurementCount = 0

        val attempts = collectAllocationAttempts(
            measure = {
                measurementCount++
                allocations("Source.kt" to 100L)
            },
            shouldRetry = { false },
        )

        assertEquals(1, measurementCount)
        assertEquals(1, attempts.size)
    }

    @Test
    fun `collects three attempts while allocation measurements keep failing`() {
        val measurements = listOf(300L, 200L, 100L).iterator()

        val attempts = collectAllocationAttempts(
            measure = { allocations("Source.kt" to measurements.next()) },
            shouldRetry = { true },
        )

        assertEquals(3, attempts.size)
        assertEquals(100L, selectLowestAllocationAttempt(attempts).totalSize())
    }

    @Test
    fun `stops retrying after successful allocation measurement`() {
        val measurements = listOf(300L, 100L, 200L).iterator()

        val attempts = collectAllocationAttempts(
            measure = { allocations("Source.kt" to measurements.next()) },
            shouldRetry = { it.totalSize() > 100L },
        )

        assertEquals(2, attempts.size)
        assertEquals(100L, selectLowestAllocationAttempt(attempts).totalSize())
    }

    @Test
    fun `collects three attempts when retry is always requested`() {
        var measurementCount = 0

        val attempts = collectAllocationAttempts(
            measure = {
                measurementCount++
                allocations("Source.kt" to measurementCount.toLong())
            },
            shouldRetry = { true },
        )

        assertEquals(3, measurementCount)
        assertEquals(3, attempts.size)
    }

    private fun allocations(vararg locations: Pair<String, Long>) = AllocationData(
        data = locations.associateTo(mutableMapOf()) { (name, size) ->
            name to LocationInfo(name = name, locationSize = size)
        }
    )

    private fun tolerance(
        allowedIncreaseRatio: Double,
        vararg locations: Pair<String, Long>,
    ) = AllocationTolerance(
        allowedIncreaseRatio = allowedIncreaseRatio,
        locations = locations.associate { (name, bytes) ->
            name to KnownLocationVariance(
                knownVarianceBytes = bytes,
                reason = "Test variance",
            )
        },
    )
}
