package io.github.youndie.mani

import io.github.youndie.mani.feature.user.currentUserId
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Маршрут, забывший `authenticate`, не отвечает так, будто владелец известен.
 *
 * `currentUserId()` раньше отдавал на этот случай 401 и ПУСТУЮ СТРОКУ, не прерывая обработчик:
 * пустой владелец уходил в хранилище, а следующий `respond` ложился поверх уже отправленного
 * ответа. Теперь вызов падает, и падение обязано выглядеть как 500 — это ошибка сервера,
 * а не запроса, и по ней должно быть видно, что маршрут не защищён.
 *
 * Хранилище тут не нужно: до него дело не доходит. Нужна только раскладка отказа в ответ, то
 * есть `StatusPages` из `configureManiPlugins`.
 */
class UnprotectedRouteTest {
    @Test
    fun `a route outside authenticate cannot ask who is calling`() = runBlocking {
        testApplication {
            application {
                configureManiPlugins(TestMongo.config())
                routing {
                    get("/leak") { call.respond(call.currentUserId()) }
                }
            }

            // Вернись пустой владелец обратно — здесь был бы 200 с пустым телом.
            assertEquals(HttpStatusCode.InternalServerError, client.get("/leak").status)
        }
    }
}
