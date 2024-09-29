plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.jmh)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    jmh(kotlin("stdlib"))
    jmh(libs.ktor.io)
    jmh(libs.ktor.utils)
    jmh(libs.ktor.network)
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