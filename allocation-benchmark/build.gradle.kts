plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("test-server")
}

group = "io.ktor"
version = "0.0.1"

val instrumenter by configurations.creating

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
}

val agentPath = instrumenter.singleOrNull()?.path
check(agentPath != null) { "Instrumentation agent is not found. Please check the configuration" }

tasks.test {
    jvmArgs = listOf("-javaagent:$agentPath")
    systemProperty("kotlinx.coroutines.debug", "off")
    useJUnitPlatform()
}

tasks.register<Test>("dumpAllocations") {
    group = "verification"
    systemProperty("SAVE_REPORT", "true")
    systemProperty("kotlinx.coroutines.debug", "off")
    jvmArgs = listOf("-javaagent:$agentPath")
    useJUnitPlatform()
}

tasks.register<JavaExec>("reportServer") {
    group = "verification"
    description = "Run a small server to show allocation reports."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "benchmarks.ReportServerKt"
}
