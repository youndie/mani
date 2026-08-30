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
        // Gradle-плагин viddik: на портале плагинов его нет, как и общих соглашений сборки.
        // Здесь это пишется руками: `pluginManagement` вычисляется до того, как применён хоть один
        // settings-плагин, — включая тот, который через него же и достают.
        //
        // С фильтром, которого не было: без него репозиторий участвует в резолве КАЖДОГО плагина,
        // то есть его спрашивают про координаты, которых он никогда не держал, — а в день, когда
        // хост недоступен, Gradle его отключает и роняет плагины, которых там и не было.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("ru\\.workinprogress.*") }
        }
    }
}

plugins {
    // Откуда берутся зависимости: google() и mavenCentral() со своими групповыми фильтрами и
    // reposilite `/snapshots` — те же три, что этот файл объявлял сам, только фильтр на последнем
    // теперь есть. Ниже остаётся то, что принадлежит этому репозиторию.
    id("ru.workinprogress.sborka.settings") version "0.1.0.20"
}

dependencyResolutionManagement {
    repositories {
        // Desktop target has to add this repo.
        // Содержимое ограничено: репозиторий бывает недоступен, и без фильтра Gradle идёт в него
        // за **любой** новой зависимостью, отключает по сетевой ошибке и роняет резолв целиком.
        maven("https://jogamp.org/deployment/maven") {
            content { includeGroupByRegex("org\\.jogamp.*") }
        }
        // Релизная линия того же reposilite: соглашения объявляют только `/snapshots`, а viddik и
        // соседние библиотеки лежат и здесь. Фильтр по той же причине, что у jogamp выше.
        maven("https://reposilite.kotlin.website/releases") {
            name = "wip-releases"
            mavenContent { includeGroupByRegex("ru\\.workinprogress.*") }
        }
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
