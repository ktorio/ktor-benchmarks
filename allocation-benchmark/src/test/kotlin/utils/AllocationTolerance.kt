package utils.benchmarks

import benchmarks.AllocationData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.math.roundToLong

private const val TOLERANCES_FILE = "allocations/tolerances.json"

@OptIn(ExperimentalSerializationApi::class)
private val toleranceMetadata by lazy {
    val toleranceFile = Path(TOLERANCES_FILE)
    check(toleranceFile.exists()) { "Allocation tolerance metadata not found: $TOLERANCES_FILE" }
    Json.decodeFromStream<AllocationToleranceMetadata>(toleranceFile.inputStream())
}

fun loadAllocationTolerance(reportName: String): AllocationTolerance =
    toleranceMetadata.toleranceFor(reportName)

private const val ALLOCATION_ATTEMPT_COUNT = 3

fun collectAllocationAttempts(
    measure: () -> AllocationData,
    shouldRetry: (AllocationData) -> Boolean,
): List<AllocationData> = buildList {
    repeat(ALLOCATION_ATTEMPT_COUNT) {
        val attempt = measure()
        add(attempt)
        if (!shouldRetry(attempt)) return@buildList
    }
}

fun selectLowestAllocationAttempt(attempts: List<AllocationData>): AllocationData {
    require(attempts.isNotEmpty()) { "At least one allocation attempt is required" }
    return attempts.minBy(AllocationData::totalSize)
}

fun compareAllocations(
    previous: AllocationData,
    current: AllocationData,
    tolerance: AllocationTolerance,
): AllocationComparisonResult {
    val locationNames = previous.packages.map { it.name }.toSet() + current.packages.map { it.name }.toSet()
    val locationDifferences = locationNames.map { locationName ->
        LocationDifference(
            locationName = locationName,
            previousSize = previous[locationName]?.locationSize ?: 0L,
            currentSize = current[locationName]?.locationSize ?: 0L,
        )
    }
    val totalDifference = current.totalSize() - previous.totalSize()
    val allowedDifference = (tolerance.allowedIncreaseRatio * previous.totalSize())
        .roundToLong()
        .coerceAtLeast(tolerance.minimumAllowedIncreaseBytes)
    val consumedLocationVariances = if (totalDifference > allowedDifference) {
        locationDifferences.mapNotNull { difference -> tolerance.tryConsumeVariance(difference) }
    } else {
        emptyList()
    }

    val consumedKnownVariance = consumedLocationVariances.sumOf { it.consumedBytes }
    val adjustedDifference = totalDifference - consumedKnownVariance
    val unexpectedIncrease = (adjustedDifference - allowedDifference).coerceAtLeast(0L)

    return AllocationComparisonResult(
        totalDifference = totalDifference,
        consumedKnownVariance = consumedKnownVariance,
        adjustedDifference = adjustedDifference,
        allowedDifference = allowedDifference,
        unexpectedIncrease = unexpectedIncrease,
        locationDifferences = locationDifferences,
        consumedLocationVariances = consumedLocationVariances,
    )
}


@Serializable
data class AllocationToleranceMetadata(
    val defaultAllowedIncreaseRatio: Double,
    val defaultMinimumAllowedIncreaseBytes: Long = 0L,
    val reports: Map<String, ReportTolerance> = emptyMap(),
) {
    init {
        require(defaultAllowedIncreaseRatio >= 0.0) {
            "defaultAllowedIncreaseRatio must not be negative"
        }
        require(defaultMinimumAllowedIncreaseBytes >= 0L) {
            "defaultMinimumAllowedIncreaseBytes must not be negative"
        }
    }

    fun toleranceFor(reportName: String): AllocationTolerance = AllocationTolerance(
        allowedIncreaseRatio = defaultAllowedIncreaseRatio,
        locations = reports[reportName]?.locations.orEmpty(),
        minimumAllowedIncreaseBytes = defaultMinimumAllowedIncreaseBytes,
    )
}

@Serializable
data class ReportTolerance(
    val locations: Map<String, KnownLocationVariance> = emptyMap(),
)

@Serializable
data class KnownLocationVariance(
    val knownVarianceBytes: Long,
    val reason: String,
    val reference: String? = null,
) {
    init {
        require(knownVarianceBytes >= 0L) { "knownVarianceBytes must not be negative" }
        require(reason.isNotBlank()) { "reason must not be blank" }
    }

    fun tryConsumeVariance(difference: LocationDifference): ConsumedLocationVariance? {
        val consumedBytes = minOf(
            difference.difference.coerceAtLeast(0L),
            knownVarianceBytes,
        )
        if (consumedBytes == 0L) return null
        return ConsumedLocationVariance(difference.locationName, consumedBytes, this)
    }
}

data class AllocationTolerance(
    val allowedIncreaseRatio: Double,
    val locations: Map<String, KnownLocationVariance>,
    val minimumAllowedIncreaseBytes: Long = 0L,
) {

    fun tryConsumeVariance(difference: LocationDifference): ConsumedLocationVariance? =
        locations[difference.locationName]?.tryConsumeVariance(difference)
}

data class LocationDifference(
    val locationName: String,
    val previousSize: Long,
    val currentSize: Long,
) {
    val difference: Long = currentSize - previousSize
}

data class ConsumedLocationVariance(
    val locationName: String,
    val consumedBytes: Long,
    val configuredVariance: KnownLocationVariance,
)

data class AllocationComparisonResult(
    val totalDifference: Long,
    val consumedKnownVariance: Long,
    val adjustedDifference: Long,
    val allowedDifference: Long,
    val unexpectedIncrease: Long,
    val locationDifferences: List<LocationDifference>,
    val consumedLocationVariances: List<ConsumedLocationVariance>,
) {
    val exceedsDefaultTolerance: Boolean get() = totalDifference > allowedDifference
}
