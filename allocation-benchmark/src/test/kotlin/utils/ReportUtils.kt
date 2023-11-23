package benchmarks

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val serializer = Json {
    prettyPrint = true
}

fun saveReport(name: String, report: AllocationData) {
    val file = File("allocations/$name.json")
    if (!file.exists()) {
        file.createNewFile()
    }

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

fun saveSiteStatistics(name: String, report: AllocationData) {
    val file = File("allocations/sites_$name.json")
    if (!file.exists()) {
        file.createNewFile()
    }

    val sites: List<SiteWithName> =
        report.packages
            .flatMap { it.instances }
            .flatMap { it.sites.values.map { site -> SiteWithName(it.name, site.stackTrace, site.totalCount, site.totalSize) } }
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
