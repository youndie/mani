plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("io.github.youndie.sborka.base")
    id("io.github.youndie.sborka.lint")
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
