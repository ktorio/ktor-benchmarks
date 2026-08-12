package allocations

import org.gradle.api.Project

fun Project.resolveAllocationBaseline(ktorVersion: String): String =
    resolveAllocationBaseline(
        explicitBaseline = providers.gradleProperty("allocationBaseline").orNull,
        ktorVersion = ktorVersion,
    )

internal fun resolveAllocationBaseline(
    explicitBaseline: String?,
    ktorVersion: String,
): String {
    explicitBaseline?.let { return normalizeAllocationBaseline(it) }

    val version = Regex("^(\\d+)\\.\\d+\\.(\\d+)").find(ktorVersion)
    val majorVersion = version?.groupValues?.get(1)?.toInt()
    val patchVersion = version?.groupValues?.get(2)?.toInt()
    check(majorVersion != null && patchVersion != null && patchVersion != 0) {
        val majorHint = majorVersion?.toString() ?: "MAJOR"
        "Cannot infer an allocation baseline from Ktor version '$ktorVersion'. " +
                "Set the allocationBaseline Gradle property to 'main' or 'release-$majorHint.x'."
    }
    return releaseAllocationBaseline(majorVersion)
}

private fun baselineForBranch(branch: String?): String? {
    val normalizedBranch = branch?.removePrefix("refs/heads/")
    if (normalizedBranch == "main") return "main"

    val majorVersion = normalizedBranch
        ?.let { Regex("^release/(\\d+)\\.x$").matchEntire(it) }
        ?.groupValues
        ?.get(1)
        ?.toInt()
    return majorVersion?.let(::releaseAllocationBaseline)
}

private fun releaseAllocationBaseline(majorVersion: Int): String = "release-$majorVersion.x"

private fun normalizeAllocationBaseline(baseline: String): String {
    val normalizedBaseline = baselineForBranch(baseline) ?: baseline
    require(normalizedBaseline == "main" || Regex("^release-\\d+\\.x$").matches(normalizedBaseline)) {
        "Unsupported allocation baseline '$baseline'. Expected 'main', 'release/MAJOR.x', or 'release-MAJOR.x'."
    }
    return normalizedBaseline
}
