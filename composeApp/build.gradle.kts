@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.pluginSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.viddik)
    id("ru.workinprogress.sborka.base")
    id("ru.workinprogress.sborka.lint")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")

    stabilityConfigurationFiles = listOf(layout.projectDirectory.file("stability_config.conf"))
}

kotlin {
    android {
        namespace = "ru.workinprogress.mani"
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

        androidResources { enable = true }

        packaging {
            resources {
                excludes += "/META-INF/AL2.0"
                excludes += "/META-INF/LGPL2.1"
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "mani"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path

            commonWebpackConfig {
                outputFileName = "mani.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static =
                            (static ?: mutableListOf()).apply {
                                add(rootDirPath)
                                add(projectDirPath)
                            }
                    }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.uiTest)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlin.test)
                implementation(libs.koin.test)
                // MapSettings — хранилище в памяти для тестов кэша.
                implementation(libs.multiplatform.settings.test)

                // Скриншот-тесты: viddik рисует Compose в настоящем Skiko-окне и пишет PNG.
                // Иначе сверять экраны с макетом нечем — приложение headless не запускается.
                // Сами артефакты, процессор и рантайм JUnit 5 приезжают с плагином
                // `ru.workinprogress.viddik`, здесь их объявлять больше не нужно.
            }
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.preference.ktx)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.profileinstaller)
            implementation(libs.androidx.compose.ui.tooling.preview)
        }
        commonMain.dependencies {
            implementation(libs.bignum)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.materialIconsExtended)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.compose.shimmer)

            implementation(libs.navigation.compose)
            // compose-charts is vendored under src/commonMain/kotlin/ir/ehsannarmani/compose_charts
            // (from the 0.0.17-js4 fork) because the published fork targets Compose 1.9's skiko ABI.
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.make.observable)

            api(projects.shared)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(compose.uiTest)
        }

        desktopMain.dependencies {
            implementation(libs.appframe.desktop)
            implementation(libs.ktor.client.cio)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// The Compose/Kotlin wasm pipeline generates an app bundle that imports "./skiko.mjs"
// at runtime, but the browser distribution Sync task does not include the skiko JS/WASM
// glue by default. Without this, the SPA index.html fallback is served for skiko.mjs and
// wasm instantiation fails with "function import requires a callable".
tasks.named<Sync>("wasmJsBrowserDistribution") {
    from(tasks.named("processSkikoRuntimeForKWasm")) {
        include("skiko.mjs", "skiko.wasm")
    }
}

compose.desktop {
    application {
        mainClass = "ru.workinprogress.mani.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ru.workinprogress.mani"
            packageVersion = "1.0.0"
        }
    }
}

// Скриншоты живут отдельной задачей (`viddikVerify`), а не в `desktopTest` — умолчание плагина.
// Записываются и сверяются на одной машине: в CI они не гоняются вовсе, поэтому переносимость
// голденов между ОС здесь ни при чём.
viddik {
    // По умолчанию viddik прощает 0.05% пикселей — на экране 393×852 это 167 точек. Смена цвета
    // сумм однажды прошла мимо сверки именно так (тогда порог был 0.5%). Сверяем строже, благо
    // разброса между прогонами на одной машине нет.
    tolerancePercent = 0.01
    // И без поканального допуска: ±2 на канал — это ровно тот сдвиг оттенка, который здесь и
    // нужно ловить.
    channelTolerance = 0
}

// ПАКЕТ СГЕНЕРИРОВАННЫХ РЕСУРСОВ — ЗАКРЕПЛЁН, А НЕ ВЫВЕДЕН.
//
// По умолчанию compose-resources собирает его из группы проекта и имени модуля. Группы у модулей
// здесь не было, и получалось `mani.composeapp.generated.resources` — от имени корневого проекта;
// стоило соглашениям проставить группу всем модулям, как пакет уехал в
// `ru.workinprogress.mani.composeapp.generated.resources`, и сотня импортов перестала разрешаться
// с сообщением `Unresolved reference 'mani'` — про пакет, которого никто не переименовывал.
//
// Записан явно: пакет, в который смотрят исходники, не должен зависеть от координаты, под которой
// модуль (не) публикуется.
compose.resources {
    packageOfResClass = "mani.composeapp.generated.resources"
}

// Вендоренная compose-charts: свой стиль, чужая история. Форматировать её — значит навсегда
// испортить сравнение с апстримом ради строк, которые мы не писали. Исключается файлом, а не
// правилом: плагин обходит исходники по путям, и `ktlint = disabled` из `.editorconfig` до неё уже
// не доходит.
//
// Раньше это стояло в `allprojects { }` в корне и применялось ко всем восьми модулям; лежит она в
// одном.
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { it.file.path.contains("/ir/ehsannarmani/") }
    }
}
