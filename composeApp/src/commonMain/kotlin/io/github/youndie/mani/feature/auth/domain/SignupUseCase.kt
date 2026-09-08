package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.user.UserResource
import io.github.youndie.mani.utilz.suspendRunCatching
import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

interface UserService {
    suspend fun signup(params: LoginParams): Boolean
    suspend fun signin(params: LoginParams): Tokens
}

class SignupUseCase(private val httpClient: HttpClient) : AuthUseCase() {
    override suspend fun invoke(params: LoginParams): Result<Boolean> = suspendRunCatching {
        val response = httpClient.post(UserResource()) {
            setBody(params)
        }
        when (response.status) {
            // Текст берётся у сервера, а не подставляется здесь. Раньше на месте любого 400
            // стояло «User already exist», и человек, приславший короткий пароль, читал, что
            // такое имя занято, — сообщение, не имеющее отношения к тому, что он сделал.
            HttpStatusCode.BadRequest -> {
                Result.failure(ServerException(response.bodyAsText().ifBlank { "Sign up refused" }))
            }

            HttpStatusCode.InternalServerError -> {
                Result.failure(ServerException())
            }

            else -> Result.success(true)
        }
    }.getOrElse { Result.failure(it) }
}
