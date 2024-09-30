plugins {
    kotlin("jvm") version "2.1.20-polaris-189"
    kotlin("plugin.allopen") version "2.1.20-polaris-189"
    id("me.champeau.jmh") version "0.7.2"
    id("org.jetbrains.kotlinx.atomicfu") version "0.25.0"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

val ktorVersion = "3.0.0-rc-1"

dependencies {
    jmh(kotlin("stdlib"))
    jmh("io.ktor:ktor-io:$ktorVersion")
    jmh("io.ktor:ktor-utils:$ktorVersion")
    jmh("io.ktor:ktor-network:$ktorVersion")
    jmh(kotlin("test"))
}

jmh {
    benchmarkMode.set(listOf("avgt"))
    fork.set(1)

    iterations.set(10)
    timeOnIteration.set("5s")

    warmupIterations.set(5)
    warmup.set("1s")

//    profilers.set(listOf("async:libPath=/home/leonid/Apps/async-profiler-2.0/build/libasyncProfiler.so"))
    timeUnit.set("ms")

}