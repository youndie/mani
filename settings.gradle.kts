rootProject.name = "Mani"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Desktop target has to add this repo
        maven("https://jogamp.org/deployment/maven")
        maven("https://reposilite.kotlin.website/releases")
    }
}

include(":composeApp")
include(":androidApp")
include(":iosApp")
include(":server")
include(":shared")
include(":baselineprofile")
