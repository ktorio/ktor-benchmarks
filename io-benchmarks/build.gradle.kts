
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.16.2")
    }
}

plugins {
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.21"
    id("me.champeau.jmh") version "0.6.5"
}

apply(plugin ="kotlinx-atomicfu")

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    jmh(kotlin("stdlib"))
    jmh("io.ktor:ktor-io:1.6.1")
    jmh("io.ktor:ktor-utils:1.6.1")
    jmh("io.ktor:ktor-network:1.6.1")
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