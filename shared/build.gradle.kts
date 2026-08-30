import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.pluginSerialization)
    id("ru.workinprogress.sborka.base")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    android {
        namespace = "ru.workinprogress.mani.shared"
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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static =
                            (static ?: mutableListOf()).apply {
                                // Serve sources to debug inside browser
                                add(rootDirPath)
                                add(projectDirPath)
                            }
                    }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, а не `implementation`: `Transaction.amount` — тип из bignum, то есть он
            // часть публичного контракта. Потребители (сервер, клиент) иначе обязаны объявлять
            // ту же зависимость сами и молча разъедутся по версиям.
            api(libs.bignum)

            api(libs.ktor.client.resources)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            api(libs.kotlin.test)
        }
    }
}
