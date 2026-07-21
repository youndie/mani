import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.pluginSerialization)
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
            implementation(libs.bignum)

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
