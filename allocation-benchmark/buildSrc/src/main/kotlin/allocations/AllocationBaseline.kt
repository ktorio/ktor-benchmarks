package allocations

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.Properties

private const val TEAMCITY_CONFIGURATION_PROPERTIES_FILE_PROPERTY = "teamcity.configuration.properties.file"

abstract class TeamCityPropertyValueSource : ValueSource<String, TeamCityPropertyValueSource.Parameters> {

    private val configurationPropertiesFile: File?
        get() = System.getProperty(TEAMCITY_CONFIGURATION_PROPERTIES_FILE_PROPERTY)?.let(::File)

    override fun obtain(): String? {
        val propertyName = parameters.propertyName.get()
        return configurationPropertiesFile?.loadProperties()?.getProperty(propertyName)
    }

    interface Parameters : ValueSourceParameters {
        val propertyName: Property<String>
    }
}

internal data class TeamCityProperties(
    val pullRequestTargetBranch: String?,
    val buildBranch: String?,
)

fun Project.resolveAllocationBaseline(ktorVersion: String): String {
    val pullRequestTargetBranch = teamCityProperty("teamcity.pullRequest.target.branch")
    val buildBranch = teamCityProperty("teamcity.build.branch")
    val teamCityProperties = if (pullRequestTargetBranch != null || buildBranch != null) {
        TeamCityProperties(pullRequestTargetBranch, buildBranch)
    } else {
        null
    }

    return resolveAllocationBaseline(
        explicitBaseline = providers.gradleProperty("allocationBaseline").orNull,
        teamCityProperties = teamCityProperties,
        ktorVersion = ktorVersion,
    )
}

internal fun resolveAllocationBaseline(
    explicitBaseline: String?,
    teamCityProperties: TeamCityProperties?,
    ktorVersion: String,
): String {
    explicitBaseline?.let { return normalizeAllocationBaseline(it) }

    if (teamCityProperties != null) {
        val branch = teamCityProperties.pullRequestTargetBranch ?: teamCityProperties.buildBranch
        return checkNotNull(baselineForBranch(branch)) {
            "Cannot select an allocation baseline for TeamCity branch '$branch'. " +
                    "Set the allocationBaseline Gradle property explicitly."
        }
    }

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

private fun Project.teamCityProperty(propertyName: String): String? =
    providers.of(TeamCityPropertyValueSource::class.java) {
        parameters.propertyName.set(propertyName)
    }.orNull

private fun File.loadProperties(): Properties? {
    if (!isFile) return null

    return Properties().also { properties ->
        inputStream().use(properties::load)
    }
}
