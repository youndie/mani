package io.github.youndie.mani.feature.health

import io.github.youndie.mani.MANI_VERSION
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Тип сборки — единственное, чем сборки обязаны отличаться, и потому единственный
 * `expect/actual` в этом файле. Всё остальное здесь общее.
 */
expect fun serverBuildKind(): String

@Suppress(
    "ktlint:kapkan:wall-clock",
    "момент старта ЭТОГО процесса — величина по определению местная",
)
@OptIn(ExperimentalTime::class)
private val startedAt = Clock.System.now()

/**
 * Отдаёт то, ради чего проект существует: посетитель видит, что его запрос обслужил нативный
 * бинарь, а не текст в README.
 */
@Suppress(
    "ktlint:kapkan:wall-clock",
    "момент старта ЭТОГО процесса — величина по определению местная",
)
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
