package ru.workinprogress.mani

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import ru.workinprogress.mani.config.ManiConfig
import ru.workinprogress.mani.security.TokenService
import ru.workinprogress.mani.web.WebAssets
import ru.workinprogress.mani.web.webRoutes

fun main() {
    val config = ManiConfig.fromEnv()

    println("mani: старт на порту ${config.port}, mongo ${config.mongo.host}")

    embeddedServer(CIO, port = config.port) {
        maniModule(config)
    }.start(wait = true)
}

/**
 * Сборка приложения. Вынесена из [main] отдельной функцией, чтобы её можно было поднять в тесте
 * через `testApplication` — с той же проводкой, что в бою.
 */
fun Application.maniModule(config: ManiConfig) {
    configureManiPlugins(config)

    install(Koin) {
        // Логгер Koin здесь по умолчанию: `koin-logger-slf4j` — JVM-only.
        modules(coreModule(config), mongknStorageModule(config.mongo))
    }

    configureManiAuth(config, get<TokenService>())

    // Каталог статики читается **один раз на старте**, а не на каждый запрос: файлы вшиты
    // в образ и за время жизни процесса не меняются. Пустой `MANI_WEB_ROOT` — сервер без
    // фронтенда; так удобно поднимать его в тестах и локально.
    val assets = config.webRoot?.let(WebAssets::scan)
    if (assets != null) {
        println("mani: статика из ${config.webRoot}, файлов ${assets.size}")
    }

    routing {
        maniApiRouting()
        if (assets != null) webRoutes(assets)
    }
}
