package ru.workinprogress.mani

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import ru.workinprogress.mani.utilz.wasmJsApp

fun Application.configureRouting() {
    routing {
        maniApiRouting()
        // Статику JVM-сборка отдаёт из ресурсов jar'а, нативная — из каталога в образе:
        // `staticResources` под Kotlin/Native не существует.
        wasmJsApp()
    }
}
