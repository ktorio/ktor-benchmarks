import allocations.resolveAllocationBaseline

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jmh)
    id("test-server")
}

group = "io.ktor"
version = "0.0.1"

val instrumenter = configurations.create("instrumenter")

kotlin {
    jvmToolchain(21)
}

val jettyVersion = providers.gradleProperty("jettyVersion").orNull ?: "12.1.12"
val jmhBenchmark = providers.gradleProperty("jmhBenchmark").orElse("JettyServerBenchmark")
configurations.matching { it.name.startsWith("jmh", ignoreCase = true) }.configureEach {
    resolutionStrategy.eachDependency {
        if (
            requested.group.startsWith("org.eclipse.jetty") &&
            requested.group != "org.eclipse.jetty.alpn"
        ) {
            useVersion(jettyVersion)
            because("Benchmark Jetty $jettyVersion")
        }
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.jetty.jakarta)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.tomcat.jakarta)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.apache)
    testImplementation(libs.ktor.client.okhttp)
    testImplementation(libs.ktor.client.java)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)

    instrumenter(libs.instrumenter)
    implementation(libs.instrumenter)

    implementation(libs.kotlinx.serialization.json)

    jmh("org.eclipse.jetty:jetty-io:$jettyVersion")
}

sourceSets.named("jmh") {
    if (jettyVersion.startsWith("12.0.")) {
        java.exclude("**/DynamicCapacityBenchmark.java")
    }
}

jmh {
    benchmarkMode.set(listOf("avgt"))
    fork.set(3)
    profilers.set(listOf("gc"))
    includes.set(listOf(jmhBenchmark.get()))
    resultFormat.set("json")

    if (providers.gradleProperty("disableEscapeAnalysis").isPresent) {
        jvmArgsAppend.set(listOf("-XX:-DoEscapeAnalysis"))
    }
}

val agentPath = instrumenter.singleOrNull()?.path
check(agentPath != null) { "Instrumentation agent is not found. Please check the configuration" }

tasks.withType<Test>().configureEach {
    jvmArgs = listOf("-javaagent:$agentPath")
    systemProperty("kotlinx.coroutines.debug", "off")
    systemProperty("allocation.baseline", resolveAllocationBaseline(libs.versions.ktor.get()))
    useJUnitPlatform()
}

tasks.register<Test>("serverTests") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        include("**/ServerCallAllocationTest*")
    }
}

tasks.register<Test>("dumpAllocations") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("SAVE_REPORT", "true")
}

tasks.register<JavaExec>("reportServer") {
    group = "verification"
    description = "Run a small server to show allocation reports."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "benchmarks.ReportServerKt"
}
