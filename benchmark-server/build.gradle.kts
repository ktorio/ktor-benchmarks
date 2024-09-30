plugins {
    kotlin("jvm") version "2.1.20-polaris-189"
}

val ktor_version: String by project.extra

group = "io.ktor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")
}
