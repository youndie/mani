package io.github.youndie.mani

import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.security.TokenService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * JVM-сборка. Порт берёт `EngineMain` из `application.conf`, всё остальное — из переменных
 * окружения, теми же именами, что и нативная сборка.
 */
fun Application.module() {
    val config = ManiConfig.fromEnv()

    configureManiPlugins(config)

    install(Koin) {
        slf4jLogger()
        modules(coreModule(config), mongoStorageModule(config.mongo))
    }

    // Проверка токенов ставится после Koin: `TokenService` берётся из графа, а не собирается
    // вторым экземпляром на том же секрете.
    configureManiAuth(config, get<TokenService>())

    configureRouting()
}
