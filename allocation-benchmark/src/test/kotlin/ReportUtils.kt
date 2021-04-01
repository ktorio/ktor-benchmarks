package benchmarks

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val serializer = Json {
    prettyPrint = true
}

fun saveReport(name: String, report: AllocationData) {
    val file = File("allocations/${name}.json")
    if (!file.exists()) {
        file.createNewFile()
    }

    val content = serializer.encodeToString(report)
    file.bufferedWriter().use {
        it.write(content)
    }
}

fun loadReport(name: String): AllocationData {
    val file = File("allocations/${name}.json")
    check(file.exists()) { "No report found: $name" }
    val content = file.readText()
    return serializer.decodeFromString(content)
}
