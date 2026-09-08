package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.AuthResource
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.github.youndie.mani.utilz.suspendRunCatching
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*

class LoginUseCase(private val httpClient: HttpClient, private val tokenRepository: TokenRepository) : AuthUseCase() {

    override suspend operator fun invoke(params: LoginParams): Result<Boolean> = suspendRunCatching {
        val response = httpClient.post(AuthResource()) {
            setBody(params)
        }

        if (response.status == HttpStatusCode.NotFound) {
            Result.failure(UserNotFoundException())
        } else {
            val result = response.body<Tokens>()
            tokenRepository.set(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
            )
            Result.success(true)
        }
    }.getOrElse { Result.failure(ServerException(message = "Network Error", cause = it)) }
}

class UserNotFoundException : ServerException("User not found or invalid password")
