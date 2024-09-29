@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))

            version("ktor", "2.1.0")
        }
    }
}

rootProject.name = "utils-benchmarks"
