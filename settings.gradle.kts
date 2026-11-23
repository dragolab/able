enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

rootProject.name = "Able"

// Application entry
include(":app")

// Core modules
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:data")
include(":core:capability")

// Feature modules
include(":feature:home")
include(":feature:battery")
include(":feature:apps")
include(":feature:tools")
include(":feature:settings")
