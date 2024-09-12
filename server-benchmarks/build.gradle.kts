plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.allopen") version "2.0.20"
    id("me.champeau.jmh") version "0.7.2"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val okhttpVersion = "4.12.0"
val apacheHttpClientVersion = "4.5.14"
val logbackVersion = "1.5.7"
val ktor_version = "3.0.0-rc-1"

repositories {
    mavenCentral()
}

dependencies {
    jmh(kotlin("stdlib"))
    jmh("io.ktor:ktor-server-core:$ktor_version")
    jmh("io.ktor:ktor-server-cio:$ktor_version")
    jmh("io.ktor:ktor-server-jetty:$ktor_version")
    jmh("io.ktor:ktor-server-netty:$ktor_version")
    jmh("io.ktor:ktor-client-core:$ktor_version")
    jmh("io.ktor:ktor-client-cio:$ktor_version")
    jmh("io.ktor:ktor-server-test-host:$ktor_version")

    jmh("org.apache.httpcomponents:httpclient:$apacheHttpClientVersion")
    jmh("com.squareup.okhttp3:okhttp:$okhttpVersion")
    jmh("ch.qos.logback:logback-classic:$logbackVersion")
}
