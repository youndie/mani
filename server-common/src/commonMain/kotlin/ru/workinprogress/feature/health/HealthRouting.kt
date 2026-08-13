package ru.workinprogress.feature.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Версия приложения. Одна на обе сборки — они собираются из одного кода. */
const val MANI_VERSION = "1.4.2"

/**
 * Тип сборки — единственное, чем сборки обязаны отличаться, и потому единственный
 * `expect/actual` в этом файле. Всё остальное здесь общее.
 */
expect fun serverBuildKind(): String

@OptIn(ExperimentalTime::class)
private val startedAt = Clock.System.now()

/**
 * Отдаёт то, ради чего проект существует: посетитель видит, что его запрос обслужил нативный
 * бинарь, а не текст в README.
 */
@OptIn(ExperimentalTime::class)
fun Routing.healthRouting() {
    get<HealthResource> {
        call.respond(
            HttpStatusCode.OK,
            Health(
                build = serverBuildKind(),
                version = MANI_VERSION,
                uptimeSeconds = (Clock.System.now() - startedAt).inWholeSeconds,
            ),
        )
    }
}
