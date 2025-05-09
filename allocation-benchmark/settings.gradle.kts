@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenCentral()
    }
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

            val ktorVersion: String? by settings.extra
            ktorVersion?.let { version("ktor", it) }
        }
    }
}

rootProject.name = "allocation-benchmark"

// For client benchmarks
includeBuild("../benchmark-server")
