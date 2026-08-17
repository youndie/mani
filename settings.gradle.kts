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
        // Gradle-плагин viddik: на портале плагинов его нет.
        maven("https://reposilite.kotlin.website/snapshots")
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
        // Desktop target has to add this repo.
        // Содержимое ограничено: репозиторий бывает недоступен, и без фильтра Gradle идёт в него
        // за **любой** новой зависимостью, отключает по сетевой ошибке и роняет резолв целиком.
        maven("https://jogamp.org/deployment/maven") {
            content { includeGroupByRegex("org\\.jogamp.*") }
        }
        maven("https://reposilite.kotlin.website/releases")
        // mongkn — драйвер MongoDB для Kotlin/Native. Публикуется в snapshots, релизной линии
        // у него пока нет; версия в каталоге зафиксирована, поэтому сборка воспроизводима.
        maven("https://reposilite.kotlin.website/snapshots")
    }
}

include(":composeApp")
include(":androidApp")
include(":iosApp")
include(":server")
include(":server-common")
include(":server-native")
include(":shared")
include(":baselineprofile")
