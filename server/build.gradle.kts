import io.ktor.plugin.features.*

plugins {
    alias(libs.plugins.jib)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.pluginSerialization)
    application
}

/**
 * JVM-сборка сервера.
 *
 * После появления `:server-native` этот модуль — тонкая обёртка: `main`, реализации хранилища на
 * официальном драйвере и упаковка образа. Маршруты, DI и аутентификация живут в `:server-common`
 * и общие с нативной сборкой.
 *
 * Сборка остаётся: она нужна для разработки на macOS, где нативный таргет не собрать вовсе, и
 * держит общую часть честной — всё, что перестанет компилироваться под JVM, ломается здесь.
 */
group = "ru.workinprogress.mani"
version = "0.2.${providers.gradleProperty("BUILD_NUMBER").getOrElse("snapshot")}"

application {
    mainClass.set("ru.workinprogress.mani.ApplicationKt")
    applicationDefaultJvmArgs =
        listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "true"}")
}

dependencies {
    implementation(projects.shared)
    implementation(projects.serverCommon)

    implementation(libs.bignum)
    implementation(libs.logback)
    implementation(libs.slf4j.api)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.mongodb.driver.kotlin.coroutine)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)

    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    testImplementation(libs.de.flapdoodle.embed.mongo)
}

// `from(taskProvider)` carries the task dependency, so no explicit dependsOn is needed
// and nothing reaches across projects at execution time (configuration-cache safe).
val copyFrontend by tasks.registering(Copy::class) {
    from(
        project(rootProject.projects.composeApp.path)
            .tasks
            .named("wasmJsBrowserDistribution"),
    ) {
        include(
            "index.html",
            "manifest.json",
            "**/*.js",
            "**/*.mjs",
            "**/*.wasm",
            "composeResources/**/*",
            "styles.css",
        )
    }

    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("processResources") { dependsOn(copyFrontend) }

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("mani-backend")
        imageTag.set("0.2.${providers.gradleProperty("BUILD_NUMBER").getOrElse("snapshot")}")
        customBaseImage.set("gcr.io/distroless/java21-debian12")
        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
                username = providers.gradleProperty("REGISTRY_USERNAME"),
                password = providers.gradleProperty("REGISTRY_PASSWORD"),
                project = provider { "mani-kotlin-fullstack" },
                hostname = providers.gradleProperty("REGISTRY_HOSTNAME"),
            ),
        )
    }
}
