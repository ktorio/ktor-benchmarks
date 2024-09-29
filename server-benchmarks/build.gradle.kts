plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.jmh)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    jmh(kotlin("stdlib"))
    jmh(libs.ktor.server.core)
    jmh(libs.ktor.server.cio)
    jmh(libs.ktor.server.jetty.jakarta)
    jmh(libs.ktor.server.netty)
    jmh(libs.ktor.client.core)
    jmh(libs.ktor.client.cio)
    jmh(libs.ktor.server.test.host)

    jmh(libs.apache.httpclient)
    jmh(libs.okhttp)
    jmh(libs.logback.classic)
}
