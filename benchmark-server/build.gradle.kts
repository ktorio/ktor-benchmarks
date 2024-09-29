plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.ktor"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.websockets)
}
