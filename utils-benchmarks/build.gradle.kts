buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.25.0")
    }
}

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.allopen") version "2.0.208"
    kotlin("plugin.serialization") version "2.0.20"
    id("me.champeau.jmh") version "0.6.5"
}


apply(plugin = "kotlinx-atomicfu")

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val serialization_version= "1.7.1"
val instrumenter by configurations.creating
val instrumenterName = "java-allocation-instrumenter"
val instrumenter_version = "3.3.4"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("junit:junit:4.13.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
    implementation("io.ktor:ktor-io:2.1.0")
    implementation("io.ktor:ktor-utils:2.1.0")
    implementation("io.ktor:ktor-network:2.1.0")

    instrumenter("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")
    implementation("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")

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

val agentPath = instrumenter.toList().find {
    it.name.contains("$instrumenterName-$instrumenter_version.jar")
}?.path

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
