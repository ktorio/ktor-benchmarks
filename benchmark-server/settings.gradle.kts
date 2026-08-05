@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("../build-settings-logic")
}

plugins {
    // Build optimizations
    id("ktorsettings.cache-redirector")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
        }
    }
}

rootProject.name = "benchmark-server"
