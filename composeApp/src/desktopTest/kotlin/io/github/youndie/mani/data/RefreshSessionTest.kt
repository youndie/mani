package io.github.youndie.mani.data

import io.github.youndie.mani.feature.auth.data.TokenRepositoryCommon
import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Что происходит, когда сервер отказывается продлевать сессию.
 *
 * Раньше на 401 токены сбрасывались, а тело ответа всё равно разбиралось — и разбор падал на
 * пустом теле. Наружу это выходило отказом сети: главный экран показывал «сервер недоступен»
 * с обратным отсчётом и тремя повторами, и пути на витрину из него не было.
 */
class RefreshSessionTest {

    private class InMemoryStorage(private var tokens: BearerTokens?) : TokenStorage {
        override fun load(): BearerTokens? = tokens

        override fun save(bearerTokens: BearerTokens) {
            tokens = bearerTokens
        }
    }

    private fun client(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(Resources)
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Тот же тип по умолчанию, что ставит боевой клиент: без него `ContentNegotiation`
        // не берётся сериализовать тело и запрос падает ещё до отправки.
        defaultRequest { contentType(ContentType.Application.Json) }
    }

    private fun repository(refreshToken: String) =
        TokenRepositoryCommon(InMemoryStorage(BearerTokens("access", refreshToken)))

    @Test
    fun aRefusedRefreshEndsTheSessionAndSaysSo() = runTest {
        val tokens = repository("stale")
        val heard = mutableListOf<Unit>()
        backgroundScope.launch { tokens.expired.collect { heard += it } }
        testScheduler.runCurrent()

        val result = refreshSession(client(HttpStatusCode.Unauthorized, ""), tokens)
        testScheduler.runCurrent()

        assertNull(result, "продлевать нечего — пары быть не должно")
        assertEquals("", tokens.getToken().refreshToken, "токены не сброшены")
        assertEquals(1, heard.size, "об истёкшей сессии никто не узнал")
    }

    /** Отказ сервера — не конец сессии: 500 не повод разлогинивать. */
    @Test
    fun aServerFailureLeavesTheSessionAlone() = runTest {
        val tokens = repository("good")
        val heard = mutableListOf<Unit>()
        backgroundScope.launch { tokens.expired.collect { heard += it } }
        testScheduler.runCurrent()

        val result = refreshSession(client(HttpStatusCode.InternalServerError, ""), tokens)
        testScheduler.runCurrent()

        assertNull(result)
        assertEquals("good", tokens.getToken().refreshToken, "сессия отменена из-за сбоя сервера")
        assertTrue(heard.isEmpty())
    }

    @Test
    fun aGoodRefreshStoresTheNewPair() = runTest {
        val tokens = repository("good")

        val result = refreshSession(
            client(HttpStatusCode.OK, """{"accessToken":"fresh-access","refreshToken":"fresh-refresh"}"""),
            tokens,
        )

        assertNotNull(result)
        assertEquals("fresh-access", tokens.getToken().accessToken)
        assertEquals("fresh-refresh", tokens.getToken().refreshToken)
    }

    /** Нечего предъявить — незачем и ходить: это первый запуск, а не истёкшая сессия. */
    @Test
    fun anEmptyRefreshTokenAsksNothing() = runTest {
        val tokens = repository("")

        assertNull(refreshSession(client(HttpStatusCode.OK, ""), tokens))
    }
}
