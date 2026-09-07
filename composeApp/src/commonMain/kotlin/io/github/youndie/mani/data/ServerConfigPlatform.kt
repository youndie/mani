package io.github.youndie.mani.data

import io.github.youndie.mani.ServerConfig
import io.github.youndie.mani.currentServerConfig

/**
 * Куда клиент ходит за API — с возможностью решить это в момент запуска, а не при сборке.
 *
 * Раньше адрес был вкомпилирован: чтобы поднять стенд у себя, требовалось отредактировать
 * `Constants.kt` и пересобрать. Теперь платформа может ответить сама, и `docker compose up`
 * работает без единой правки в коде.
 *
 * `null` значит «нечего сказать» — тогда берётся вкомпилированный адрес.
 */
expect fun platformServerConfig(): ServerConfig?

/** Адрес, которым пользуется весь клиент. */
val serverConfig: ServerConfig by lazy { platformServerConfig() ?: currentServerConfig }
