package io.github.youndie.mani.feature.demo

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.demo.domain.SeedDemoDataUseCase
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Засев своего аккаунта с клиента.
 *
 * Главное здесь — перечитывание после успеха: записи заводит сервер, и в клиентском хранилище
 * о них нет ничего. Без него человек нажимает кнопку на пустом экране, данные создаются, а экран
 * остаётся пустым — то есть кнопка выглядит сломанной, хотя сработала.
 */
class SeedDemoDataUseCaseTest {

    private fun client(status: HttpStatusCode) = HttpClient(
        MockEngine { respond(ByteReadChannel(""), status) },
    ) {
        install(Resources)
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest { contentType(ContentType.Application.Json) }
    }

    @Test
    fun aSeededAccountIsReloaded() = runTest {
        val transactions = FakeTransactionsRepository()

        val result = SeedDemoDataUseCase(client(HttpStatusCode.Created), transactions)()

        assertTrue(result.isSuccess)
        assertTrue(
            transactions.dataStateFlow.value.isNotEmpty(),
            "список не перечитан — экран остался бы пустым при созданных данных",
        )
    }

    /** Отказ сервера — отказ наружу, и перечитывать нечего. */
    @Test
    fun aRefusedSeedDoesNotReload() = runTest {
        val transactions = FakeTransactionsRepository()

        val result = SeedDemoDataUseCase(client(HttpStatusCode.InternalServerError), transactions)()

        assertIs<ServerException>(result.exceptionOrNull())
        assertTrue(transactions.dataStateFlow.value.isEmpty(), "список перечитан после отказа")
    }
}
