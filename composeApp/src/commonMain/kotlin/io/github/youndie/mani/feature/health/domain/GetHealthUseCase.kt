package io.github.youndie.mani.feature.health.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.health.Health
import io.github.youndie.mani.feature.health.HealthResource
import io.github.youndie.mani.useCase.EmptyParams
import io.github.youndie.mani.useCase.NonParameterizedUseCase
import io.github.youndie.mani.utilz.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Спрашивает у сервера, чем он собран.
 *
 * Нужно ровно затем, ради чего проект существует: на экране входа посетитель видит, что его
 * запрос обслужил нативный бинарь, а не читает об этом в README. Значение приходит живым
 * ответом — константа в клиенте не доказывала бы ничего.
 */
abstract class HealthUseCase : NonParameterizedUseCase<Health>()

class GetHealthUseCase(private val httpClient: HttpClient) : HealthUseCase() {
    override suspend operator fun invoke(params: EmptyParams): Result<Health> = suspendRunCatching {
        withContext(Dispatchers.Default) {
            Result.success(httpClient.get(HealthResource()).body<Health>())
        }
    }.getOrElse { Result.failure(ServerException(message = "Couldn't reach the server", cause = it)) }
}
