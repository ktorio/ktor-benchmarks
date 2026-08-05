@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("../build-settings-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("ktorsettings.cache-redirector")
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap") {
            content { includeGroupAndSubgroups("io.ktor") }
        }
        mavenCentral()
        mavenLocal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))

            val ktorVersion = settings.providers.gradleProperty("ktorVersion").orNull
            ktorVersion?.let { version("ktor", it) }
        }
    }
}

rootProject.name = "allocation-benchmark"

// For client benchmarks
includeBuild("../benchmark-server")
