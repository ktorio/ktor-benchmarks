package utils.benchmarks

import benchmarks.AllocationData
import benchmarks.InstanceData
import benchmarks.LocationInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToLong

private val serializer = Json {
    prettyPrint = true
}

fun saveReport(name: String, report: AllocationData, replace: Boolean = true) {
    val file = if (replace)
        File("allocations/$name.json")
    else
        File("build/allocations/$name.json")

    if (!file.parentFile.exists())
        file.parentFile.mkdirs()

    if (!file.exists())
        file.createNewFile()

    val content = serializer.encodeToString(report)
    file.bufferedWriter().use {
        it.write(content)
    }
}

@Serializable
data class SiteWithName(
    val name: String,
    val stackTrace: String,
    var totalCount: Long,
    var totalSize: Long
)

fun saveSiteStatistics(name: String, report: AllocationData, replace: Boolean) {
    val file = if (replace)
        File("allocations/sites_$name.json")
    else
        File("build/allocations/sites_$name.json")

    if (!file.exists()) {
        file.createNewFile()
    }

    val sites: List<SiteWithName> =
        report.packages
            .flatMap { it.instances }
            .flatMap { it.sites.values.map { site ->
                SiteWithName(it.name, site.stackTrace, site.totalCount, site.totalSize) }
            }
            .sortedByDescending { it.totalSize }

    val content = serializer.encodeToString(sites)

    file.bufferedWriter().use {
        it.write(content)
    }
}

fun loadReport(name: String): AllocationData {
    val file = File("allocations/$name.json")
    check(file.exists()) { "No report found: $name" }
    val content = file.readText()
    return serializer.decodeFromString(content)
}

fun normalizeReport(report: AllocationData, requestCount: Long): AllocationData =
    report.copy(data = report.data.mapValuesTo(mutableMapOf()) { (_, value) ->
        value.copy(
            locationSize = value.locationSize.divRounded(requestCount),
            instanceIndex = normalizeInstanceIndex(value.instanceIndex, requestCount)
        )
    })

private fun normalizeInstanceIndex(
    instances: MutableMap<String, InstanceData>,
    requestCount: Long
): MutableMap<String, InstanceData> =
    instances.mapValuesTo(mutableMapOf()) { (_, instanceData) ->
        instanceData.copy(
            sites = instanceData.sites.mapValuesTo(mutableMapOf()) { (_, statistics) ->
                statistics.copy(
                    totalCount = statistics.totalCount.divRounded(requestCount),
                    totalSize = statistics.totalSize.divRounded(requestCount),
                )
            },
            totalSize = instanceData.totalSize.divRounded(requestCount)
        )
    }

private fun Long.divRounded(value: Long): Long = (toDouble() / value.toDouble()).roundToLong()