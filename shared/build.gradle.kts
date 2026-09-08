import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.pluginSerialization)
    id("io.github.youndie.sborka.base")
    id("io.github.youndie.sborka.lint")
}

kotlin {
    android {
        namespace = "io.github.youndie.mani.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    // Ради нативного сервера: контракт (`@Resource`-классы, модель, сериализаторы) один на
    // клиента и обе сборки сервера, и без этого таргета `:server-common` не слинкуется.
    linuxX64()

    // Без настройки dev-сервера: она была скопирована из `:composeApp`, а у библиотеки нет ни
    // страницы, ни сервера, который её отдаёт, — webpack здесь только собирает klib в модуль.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, а не `implementation`: `Transaction.amount` — тип из bignum, то есть он
            // часть публичного контракта. Потребители (сервер, клиент) иначе обязаны объявлять
            // ту же зависимость сами и молча разъедутся по версиям.
            api(libs.bignum)

            api(libs.ktor.client.resources)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            api(libs.kotlin.test)
        }
    }
}
