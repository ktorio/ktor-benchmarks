plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.jmh)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val instrumenter by configurations.creating

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.junit4)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.io)
    implementation(libs.ktor.utils)
    implementation(libs.ktor.network)

    instrumenter(libs.instrumenter)
    implementation(libs.instrumenter)
}

jmh {
    benchmarkMode.set(listOf("thrpt"))
    fork.set(1)

    iterations.set(10)
    timeOnIteration.set("1s")

    warmupIterations.set(5)
    warmup.set("1s")
    jvmArgs.set(listOf("-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+TraceClassLoading -XX:+LogCompilation -XX:LogFile=compiler.log"))

//    this.benchmarkMode

//    profilers.set(listOf("async:libPath=/home/leonid/Apps/async-profiler-2.0/build/libasyncProfiler.so"))
//    profilers.set(listOf("async:libPath=/home/leonid/Apps/async-profiler-2.0/build/libasyncProfiler.so;event=alloc"))
    profilers.set(listOf("gc"))
//    profilers.set(listOf("perfasm"))
    timeUnit.set("ms")

}

val agentPath = instrumenter.singleOrNull()?.path
check(agentPath != null) { "Instrumentation agent is not found. Please check the configuration" }

tasks.test {
    jvmArgs = listOf("-javaagent:$agentPath")
    systemProperty("kotlinx.coroutines.debug", "off")
//    useJUnitPlatform()
}
//
//tasks.register<Test>("dumpAllocations") {
//    systemProperty("SAVE_REPORT", "true")
//    systemProperty("kotlinx.coroutines.debug", "off")
//    jvmArgs = listOf("-javaagent:$agentPath")
//    useJUnitPlatform()
//}
