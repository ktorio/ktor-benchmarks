plugins {
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.4.32"
    id("me.champeau.gradle.jmh") version "0.5.3"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val okhttpVersion = "4.9.3"
val apacheHttpClientVersion = "4.5.13"
val logbackVersion = "1.2.11"
val ktor_version = "2.0.1"

repositories {
    mavenCentral()
}

dependencies {
    jmh(kotlin("stdlib"))
    jmh("io.ktor:ktor-server-cio:$ktor_version")
    jmh("io.ktor:ktor-server-jetty:$ktor_version")
    jmh("io.ktor:ktor-server-netty:$ktor_version")
    jmh("io.ktor:ktor-client-cio:$ktor_version")
    jmh("io.ktor:ktor-server-test-host:$ktor_version")

    jmh("org.apache.httpcomponents:httpclient:$apacheHttpClientVersion")
    jmh("com.squareup.okhttp3:okhttp:$okhttpVersion")
    jmh("ch.qos.logback:logback-classic:$logbackVersion")
}
