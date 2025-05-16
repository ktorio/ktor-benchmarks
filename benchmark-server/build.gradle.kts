plugins {
    `kotlin-dsl`
}

group = "io.ktor"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
}
