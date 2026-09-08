package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.data.TokenRepositoryCommon
import io.github.youndie.mani.feature.auth.data.TokenStorageImpl
import io.github.youndie.mani.feature.auth.data.temporarySessionFile
import io.github.youndie.mani.useCase.EmptyParams
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Вход в песочницу.
 *
 * Проверяется не «пришёл ответ», а что после него: успех кладёт пару токенов в хранилище — иначе
 * следующий же запрос уйдёт без подписи, — а любой неуспех оставляет хранилище пустым. Половина
 * сессии хуже её отсутствия: приложение считало бы человека вошедшим и показывало ему отказы.
 */
class StartDemoUseCaseTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine { request ->
            requests += request
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(Resources)
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest { contentType(ContentType.Application.Json) }
    }

    private fun tokens() = TokenRepositoryCommon(TokenStorageImpl(temporarySessionFile()))

    @Test
    fun theSandboxHandsBackASessionAndItIsStored() = runTest {
        val tokenRepository = tokens()

        val result = StartDemoUseCase(
            client(HttpStatusCode.OK, """{"accessToken":"access-from-sandbox","refreshToken":"refresh-from-sandbox"}"""),
            tokenRepository,
        )(EmptyParams)

        assertTrue(result.isSuccess)
        assertEquals("access-from-sandbox", tokenRepository.getToken().accessToken)
        assertEquals("refresh-from-sandbox", tokenRepository.getToken().refreshToken)
    }

    /** Ввода нет: песочница заводится одним `POST /demo` без тела. */
    @Test
    fun theSandboxIsAskedForWithoutAnyInput() = runTest {
        StartDemoUseCase(
            client(HttpStatusCode.OK, """{"accessToken":"access","refreshToken":"refresh"}"""),
            tokens(),
        )(EmptyParams)

        assertEquals(1, requests.size)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals("/demo", requests.single().url.encodedPath)
    }

    @Test
    fun aRefusedSandboxLeavesTheSessionEmpty() = runTest {
        val tokenRepository = tokens()

        val result = StartDemoUseCase(client(HttpStatusCode.InternalServerError, ""), tokenRepository)(EmptyParams)

        assertIs<ServerException>(result.exceptionOrNull())
        assertEquals("", tokenRepository.getToken().accessToken)
        assertEquals("", tokenRepository.getToken().refreshToken)
    }

    /**
     * Ответ без токенов — тоже отказ.
     *
     * Иначе успех объявлялся бы по коду ответа, а сессия оставалась бы пустой: экран входа
     * закрылся, а ни один запрос дальше не подписан.
     */
    @Test
    fun anAnswerWithoutTokensIsNotHalfASession() = runTest {
        val tokenRepository = tokens()

        val result = StartDemoUseCase(client(HttpStatusCode.OK, "{}"), tokenRepository)(EmptyParams)

        assertIs<ServerException>(result.exceptionOrNull())
        assertEquals("", tokenRepository.getToken().accessToken)
    }
}
