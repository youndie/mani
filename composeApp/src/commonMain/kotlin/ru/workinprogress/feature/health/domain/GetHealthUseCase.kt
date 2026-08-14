package ru.workinprogress.feature.health.domain

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.health.Health
import ru.workinprogress.feature.health.HealthResource
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.EmptyParams
import ru.workinprogress.useCase.NonParameterizedUseCase

/**
 * Спрашивает у сервера, чем он собран.
 *
 * Нужно ровно затем, ради чего проект существует: на экране входа посетитель видит, что его
 * запрос обслужил нативный бинарь, а не читает об этом в README. Значение приходит живым
 * ответом — константа в клиенте не доказывала бы ничего.
 */
abstract class HealthUseCase : NonParameterizedUseCase<Health>()

class GetHealthUseCase(private val httpClient: HttpClient) : HealthUseCase() {
    override suspend operator fun invoke(params: EmptyParams): Result<Health> = try {
        withContext(Dispatchers.Default) {
            Result.Success(httpClient.get(HealthResource()).body())
        }
    } catch (e: Exception) {
        Result.Error(ServerException(message = "Couldn't reach the server", cause = e))
    }
}
