import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Без версии, и это не упущение. `com.android.test` лежит в том же jar, что и остальные плагины
    // AGP, а его версию корневой скрипт уже положил на classpath через `wip.plugins.androidApplication`.
    // В каталоге `wip` этого id нет, а держать ради одной строки свой ключ `agp` значит снова завести
    // второе число, которое разъедется с общим.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
    id("io.github.youndie.sborka.base")
    id("io.github.youndie.sborka.lint")
}

android {
    namespace = "io.github.youndie.mani.baselineprofile"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 28
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":androidApp"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId },
        )
    }
}
