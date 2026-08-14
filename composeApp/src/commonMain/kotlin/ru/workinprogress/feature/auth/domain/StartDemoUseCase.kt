package ru.workinprogress.feature.auth.domain

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.demo.DemoResource
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.EmptyParams

/**
 * Вход в песочницу: сервер сам заводит одноразового пользователя с готовыми данными и отдаёт
 * токены. Ввода нет — «no account, no password», в этом и смысл кнопки.
 *
 * Отличается от [LoginUseCase] только отсутствием параметров: дальше всё то же самое — токены
 * ложатся в [TokenRepository], и приложение не знает, каким путём посетитель вошёл.
 */
class StartDemoUseCase(private val httpClient: HttpClient, private val tokenRepository: TokenRepository) :
    DemoUseCase() {
    override suspend operator fun invoke(params: EmptyParams): Result<Boolean> = try {
        withContext(Dispatchers.Default) {
            val tokens = httpClient.post(DemoResource()).body<Tokens>()

            tokenRepository.set(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            )
            Result.Success(true)
        }
    } catch (e: Exception) {
        Result.Error(ServerException(message = "Couldn't start the demo", cause = e))
    }
}
