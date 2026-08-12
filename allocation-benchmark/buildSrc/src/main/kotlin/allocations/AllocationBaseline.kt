package allocations

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.Properties

private const val TEAMCITY_BUILD_PROPERTIES_FILE = "TEAMCITY_BUILD_PROPERTIES_FILE"

abstract class TeamCityPropertyValueSource :
    ValueSource<String, TeamCityPropertyValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val buildPropertiesFile: RegularFileProperty
        val propertyName: Property<String>
    }

    override fun obtain(): String? = readTeamCityProperty(
        parameters.buildPropertiesFile.asFile.get(),
        parameters.propertyName.get(),
    )
}

internal data class TeamCityProperties(
    val pullRequestTargetBranch: String?,
    val buildBranch: String?,
)

fun Project.resolveAllocationBaseline(ktorVersion: String): String {
    val extraProperties = extensions.extraProperties
    val parameters = if (extraProperties.has("teamcity")) {
        extraProperties["teamcity"] as Map<*, *>
    } else {
        null
    }
    val buildPropertiesFile = providers.environmentVariable(TEAMCITY_BUILD_PROPERTIES_FILE)
        .orNull
        ?.let(::File)
    val teamCityProperties = if (parameters != null || buildPropertiesFile != null) {
        TeamCityProperties(
            pullRequestTargetBranch = buildPropertiesFile
                ?.let { teamCityProperty(it, "teamcity.pullRequest.target.branch") }
                ?: parameters?.value("pullRequest.target.branch"),
            buildBranch = buildPropertiesFile
                ?.let { teamCityProperty(it, "teamcity.build.branch") }
                ?: parameters?.value("build.branch"),
        )
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

private fun Project.teamCityProperty(buildPropertiesFile: File, propertyName: String): String? =
    providers.of(TeamCityPropertyValueSource::class.java) {
        parameters.buildPropertiesFile.fileValue(buildPropertiesFile)
        parameters.propertyName.set(propertyName)
    }.orNull

internal fun readTeamCityProperty(buildPropertiesFile: File, propertyName: String): String? {
    val buildProperties = buildPropertiesFile.loadProperties() ?: return null
    buildProperties.getProperty(propertyName)?.let { return it }

    val configurationPropertiesFile = buildProperties
        .getProperty("teamcity.configuration.properties.file")
        ?.let(::File)
        ?: return null
    return configurationPropertiesFile.loadProperties()?.getProperty(propertyName)
}

private fun File.loadProperties(): Properties? {
    if (!isFile) return null

    return Properties().also { properties ->
        inputStream().use(properties::load)
    }
}

private fun Map<*, *>.value(name: String): String? =
    (get(name) ?: get("teamcity.$name"))?.toString()
