plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
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
