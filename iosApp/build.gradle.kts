plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ru.workinprogress.sborka.base")
    id("ru.workinprogress.sborka.lint")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true

            export(projects.composeApp)
            export(projects.shared)
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(projects.composeApp)
        }
    }
}
