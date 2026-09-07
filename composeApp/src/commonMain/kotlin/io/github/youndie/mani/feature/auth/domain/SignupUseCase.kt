package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.user.UserResource
import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*

interface UserService {
    suspend fun signup(params: LoginParams): Boolean
    suspend fun signin(params: LoginParams): Tokens
}

class SignupUseCase(private val httpClient: HttpClient) : AuthUseCase() {
    override suspend fun invoke(params: LoginParams) = try {
        val response = httpClient.post(UserResource()) {
            setBody(params)
        }
        when (response.status) {
            HttpStatusCode.BadRequest -> {
                Result.Error(AlreadyRegisteredException())
            }

            HttpStatusCode.InternalServerError -> {
                Result.Error(ServerException())
            }

            else -> Result.Success(true)
        }
    } catch (e: Exception) {
        Result.Error(e)
    }
}
