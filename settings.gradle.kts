pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()

        // ⭐ REQUIRED FOR USB-SERIAL LIBRARY
        maven(url = "https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ⭐ REQUIRED FOR USB-SERIAL LIBRARY
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "BabyNodeAutomotive"
include(":app")
