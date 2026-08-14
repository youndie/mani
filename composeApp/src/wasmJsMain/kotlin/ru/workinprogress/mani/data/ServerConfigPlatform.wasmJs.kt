package ru.workinprogress.mani.data

import kotlinx.browser.window
import ru.workinprogress.mani.ServerConfig

/**
 * В браузере API живёт там же, откуда пришла страница.
 *
 * Так устроен и стенд из `docker compose`, и рабочий сервер: фронтенд отдаёт тот же процесс, что
 * отвечает на запросы. Значит, спрашивать адрес не у кого — он уже в адресной строке.
 *
 * Исключение — отдельный dev-сервер webpack: он отдаёт страницу со своего порта, а API там нет.
 * Для него адрес по-прежнему задаётся в `Constants.kt`.
 */
actual fun platformServerConfig(): ServerConfig? {
    val location = window.location
    val host = location.hostname.ifEmpty { return null }

    return ServerConfig(
        name = "Origin",
        scheme = location.protocol.removeSuffix(":").ifEmpty { "http" },
        host = host,
        port = location.port.ifEmpty { null },
    )
}
