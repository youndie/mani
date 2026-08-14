package ru.workinprogress.feature.auth.domain

import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.user.UserResource
import ru.workinprogress.mani.data.ServerException

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
