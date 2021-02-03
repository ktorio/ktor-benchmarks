import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("jvm") version "1.4.21"
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenLocal()
    jcenter()
}

val instrumenter by configurations.creating
val instrumenterName = "java-allocation-instrumenter"
val instrumenter_version = "3.3.0"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-jetty:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("io.ktor:ktor-server-tomcat:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")

    instrumenter("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")
    implementation("com.google.code.java-allocation-instrumenter:$instrumenterName:$instrumenter_version")
}

val agentPath = instrumenter.toList().find {
    it.name.contains("$instrumenterName-$instrumenter_version.jar")
}?.path

check(agentPath != null) { "Instrumentation agent is not found. Please check the configuration" }

application {
    val name = "com.example.ApplicationKt"
    mainClass.set(name)
    mainClassName = name

    applicationDefaultJvmArgs = listOf(
        "-javaagent:$agentPath"
    )
}

val shadowJar: ShadowJar by tasks

shadowJar.apply {
    minimize()
}