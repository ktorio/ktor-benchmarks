plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.ktor.benchmarks.ClientBenchmarkKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.java)
    implementation(libs.okhttp)
    implementation(libs.logback.classic)
}
