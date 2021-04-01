val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val instrumenter_version: String by project
val junit_version: String by project
val serialization_version: String by project

plugins {
    kotlin("jvm") version "1.4.32"
    kotlin("plugin.serialization") version "1.4.32"
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

val instrumenter by configurations.creating
val instrumenterName = "java-allocation-instrumenter"

dependencies {
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-jetty:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("io.ktor:ktor-server-tomcat:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")

    testImplementation(platform("org.junit:junit-bom:$junit_version"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit_version")

    testImplementation("org.junit.jupiter:junit-jupiter")

    instrumenter("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")
    implementation("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
}

val agentPath = instrumenter.toList().find {
    it.name.contains("$instrumenterName-$instrumenter_version.jar")
}?.path

check(agentPath != null) { "Instrumentation agent is not found. Please check the configuration" }

tasks.test {
    jvmArgs = listOf("-javaagent:$agentPath")
    useJUnitPlatform()
}

tasks.register<Test>("dumpAllocations") {
    systemProperty("SAVE_REPORT", "true")
    jvmArgs = listOf("-javaagent:$agentPath")
    useJUnitPlatform()
}