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
    }
}

rootProject.name = "Spoldify"
include(":app")
include(":librespot-android-decoder")
include(":librespot-android-decoder-tremolo")
include(":librespot-android-sink")
project(":librespot-android-decoder").projectDir = file("librespot-android/librespot-android-decoder")
project(":librespot-android-decoder-tremolo").projectDir = file("librespot-android/librespot-android-decoder-tremolo")
project(":librespot-android-sink").projectDir = file("librespot-android/librespot-android-sink")
