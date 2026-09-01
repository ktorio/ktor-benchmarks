import allocations.createJavaAgentProvider
import allocations.resolveAllocationBaseline

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jmh)
    id("test-server")
}

group = "io.ktor"
version = "0.0.1"

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
    profilers.set(
        buildList {
            add("gc")
            if (jmhBenchmark.get().contains("JettyServerBenchmark")) {
                add("benchmarks.ServerAllocationProfiler")
            }
        }
    )
    includes.set(listOf(jmhBenchmark.get()))
    resultFormat.set("json")

    providers.gradleProperty("jmhForks").orNull?.let { fork.set(it.toInt()) }
    providers.gradleProperty("jmhResultName").orNull?.let {
        resultsFile.set(layout.buildDirectory.file("results/jmh/$it.json"))
    }

    if (providers.gradleProperty("disableEscapeAnalysis").isPresent) {
        jvmArgsAppend.set(listOf("-XX:-DoEscapeAnalysis"))
    }
}

tasks.test {
    filter {
        exclude("**/ServerCallAllocationTest*")
        exclude("**/ClientCallAllocationTest*")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("allocation.baseline", resolveAllocationBaseline(libs.versions.ktor.get()))
}


val instrumenterAgentProvider = createJavaAgentProvider("instrumenter", libs.instrumenter)
val saveReports = providers.gradleProperty("saveReports")
    .map { it.toBooleanStrict() }
    .orElse(false)

fun Project.registerAllocationTestTask(target: String): TaskProvider<Test> {
    return tasks.register<Test>("${target}Tests") {
        group = "verification"
        description = "Run $target allocation tests."
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        jvmArgumentProviders.add(instrumenterAgentProvider)
        systemProperty("kotlinx.coroutines.debug", "off")
        systemProperty("SAVE_REPORT", saveReports.get())

        filter {
            val capitalizedTarget = target.replaceFirstChar { it.uppercase() }
            include("**/${capitalizedTarget}CallAllocationTest*")
        }
    }
}

val serverTests = registerAllocationTestTask("server")
val clientTests = registerAllocationTestTask("client")

val allocationTests = tasks.register("allocationTests") {
    group = "verification"
    description = "Run all allocation tests."
    dependsOn(serverTests, clientTests)
}

tasks.check {
    dependsOn(allocationTests)
}

tasks.register<JavaExec>("reportServer") {
    group = "verification"
    description = "Run a small server to show allocation reports."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "benchmarks.ReportServerKt"
}
