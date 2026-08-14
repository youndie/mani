package ru.workinprogress.feature.auth.domain

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.auth.AuthResource
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.mani.data.ServerException

class LoginUseCase(private val httpClient: HttpClient, private val tokenRepository: TokenRepository) : AuthUseCase() {

    override suspend operator fun invoke(params: LoginParams): Result<Boolean> {
        try {
            return withContext(Dispatchers.Default) {
                val response = httpClient.post(AuthResource()) {
                    setBody(params)
                }

                if (response.status == HttpStatusCode.NotFound) {
                    Result.Error(UserNotFoundException())
                } else {
                    val result = response.body<Tokens>()
                    tokenRepository.set(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                    )
                    Result.Success(true)
                }
            }
        } catch (e: Exception) {
            return Result.Error(ServerException(message = "Network Error", cause = e))
        }
    }
}

class UserNotFoundException : ServerException("User not found or invalid password")
class AlreadyRegisteredException : ServerException("User already exist")
