package io.github.youndie.mani

import io.github.youndie.mani.utilz.wasmJsApp
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        maniApiRouting()
        // Статику JVM-сборка отдаёт из ресурсов jar'а, нативная — из каталога в образе:
        // `staticResources` под Kotlin/Native не существует.
        wasmJsApp()
    }
}
