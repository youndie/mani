package io.github.youndie.mani.feature.health

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.health.domain.GetHealthUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
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
import kotlin.test.assertNotNull

/**
 * Ответ `/health` разбирается, а недоступность превращается в отказ, а не в исключение.
 *
 * Значение приходит живым ответом — ради этого маршрут и заведён: константа в клиенте не
 * доказывала бы, какая сборка обслужила запрос.
 */
class GetHealthUseCaseTest {

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
        defaultRequest { contentType(ContentType.Application.Json) }
    }

    @Test
    fun theAnswerIsParsed() = runTest {
        val body = """{"build":"kotlin/native","version":"1.4.2","uptimeSeconds":12}"""

        val health = assertNotNull(GetHealthUseCase(client(HttpStatusCode.OK, body))().getOrNull())

        assertEquals("kotlin/native", health.build)
        assertEquals("1.4.2", health.version)
        assertEquals(12, health.uptimeSeconds)
    }

    /** Сервер не ответил — это `Result.failure`, а не исключение в корутине витрины. */
    @Test
    fun anUnreachableServerBecomesAFailure() = runTest {
        val result = GetHealthUseCase(client(HttpStatusCode.InternalServerError, ""))()

        assertIs<ServerException>(result.exceptionOrNull())
    }
}
