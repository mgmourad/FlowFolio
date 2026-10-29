pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FlowFolio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":benchmark")
include(":core:model")
include(":core:conversion")
include(":core:pdf")
include(":core:epub")
include(":core:reflow")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:pagecurl")
include(":feature:library")
include(":feature:reader")
include(":feature:reader:riffle")
include(":tools:convert")

// Throwaway spikes: built only with -Pspikes, never in CI or the shipped app.
if (providers.gradleProperty("spikes").isPresent) {
    include(":spikes:epub-pagination")
}
