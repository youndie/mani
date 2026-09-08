package io.github.youndie.mani.feature.health

import io.github.youndie.mani.MANI_VERSION
import io.github.youndie.mani.utilz.suspendRunCatching
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject
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
    val storageHealth by inject<StorageHealth>()

    /*
     * Готовность: один запрос в базу на каждую пробу.
     *
     * Отказ драйвера — это «не готов», а не 500: пробе нужен код ответа, а не разбор причины.
     * Через `suspendRunCatching`, чтобы отменённый запрос не превратился в «база лежит».
     *
     * Своего таймаута здесь нет намеренно. Обёртка вокруг блокирующего вызова его не даёт, а
     * ограничивает пробу kubelet своим `timeoutSeconds` — тем, кто и решает, сколько ждать.
     */
    get<HealthResource.Ready> {
        val reachable = suspendRunCatching { storageHealth.isReachable() }.getOrElse { false }

        if (reachable) {
            call.respond(HttpStatusCode.OK, "ready")
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, "storage unreachable")
        }
    }

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
