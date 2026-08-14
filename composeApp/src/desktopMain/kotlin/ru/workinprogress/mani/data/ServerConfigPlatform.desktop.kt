package ru.workinprogress.mani.data

import ru.workinprogress.mani.ServerConfig

/**
 * Десктоп слушает переменную окружения: `MANI_SERVER=http://localhost:8080 ./gradlew :composeApp:run`.
 *
 * Разбор нарочно грубый — это удобство для запуска стенда, а не разбор URL: адрес задаёт тот, кто
 * этот стенд и поднял.
 */
actual fun platformServerConfig(): ServerConfig? {
    val raw = System.getenv(SERVER_ENV)?.trim().orEmpty().ifEmpty { return null }

    val scheme = raw.substringBefore("://", missingDelimiterValue = "http")
    val authority = raw.substringAfter("://", missingDelimiterValue = raw).substringBefore('/')

    return ServerConfig(
        name = "Env",
        scheme = scheme,
        host = authority.substringBefore(':'),
        development = true,
        port = authority.substringAfter(':', missingDelimiterValue = "").ifEmpty { null },
    )
}

private const val SERVER_ENV = "MANI_SERVER"
