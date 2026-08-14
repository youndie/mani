package ru.workinprogress.feature.auth.domain

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.auth.data.TokenRepositoryCommon
import ru.workinprogress.feature.auth.data.TokenStorageImpl
import ru.workinprogress.mani.data.ServerException
import ru.workinprogress.useCase.UseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthUseCaseTest {

    private fun defaultHttpRequest(
        block: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            this.block(request)
        },
    ) {
        install(Resources)
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    @Test
    fun loginUserNotFoundErrorTest() = runTest {
        val tokenRepository: TokenRepository = TokenRepositoryCommon(TokenStorageImpl())
        val authUseCase: AuthUseCase = LoginUseCase(
            defaultHttpRequest {
                respond(
                    content = ByteReadChannel(""""""),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            tokenRepository,
        )

        val result = authUseCase(LoginParams("username", "password"))

        result as UseCase.Result.Error
        assertIs<UserNotFoundException>(result.throwable)
    }

    @Test
    fun loginServerErrorTest() = runTest {
        val tokenRepository: TokenRepository = TokenRepositoryCommon(TokenStorageImpl())
        val authUseCase = LoginUseCase(
            defaultHttpRequest {
                respond(
                    content = ByteReadChannel(""""""),
                    status = HttpStatusCode.InternalServerError,
                )
            },
            tokenRepository,
        )

        val result = authUseCase(LoginParams("username", "password"))

        result as UseCase.Result.Error
        assertIs<ServerException>(result.throwable)
    }

    @Test
    fun loginSuccessTest() = runTest {
        val tokenRepository: TokenRepository = TokenRepositoryCommon(TokenStorageImpl())
        val authUseCase = LoginUseCase(
            defaultHttpRequest { data ->
                respond(
                    content = ByteReadChannel(
                        """
                    {
                        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImF1ZCI6Imp3dC1hdWRpZW5jZSIsImlzcyI6Imp3dC1pc3N1ZXIiLCJpZCI6IjY3NDU4NGMxZTgzNDAyMmMxYzA3M2ZjZCIsInVzZXJuYW1lIjoidGVzdGVyIiwiZXhwIjoxNzMzMTk4MDc2fQ.q6_f2N_rKWrcOtopisHpS-CImU-aS6I_AAAGHGzN-j4",
                        "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImF1ZCI6Imp3dC1hdWRpZW5jZSIsImlzcyI6Imp3dC1pc3N1ZXIiLCJpZCI6IjY3NDU4NGMxZTgzNDAyMmMxYzA3M2ZjZCIsInVzZXJuYW1lIjoidGVzdGVyIiwiZXhwIjoxNzM1ODcyODc2fQ.zOMfZOpt67FOjkWXNsKDwr6puGsHtmTsbIE1eP4gJBc"
                    }
                        """.trimIndent(),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            tokenRepository,
        )

        val result = authUseCase(LoginParams("username", "password"))

        assertIs<UseCase.Result.Success<Boolean>>(result)
        assertEquals(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImF1ZCI6Imp3dC1hdWRpZW5jZSIsImlzcyI6Imp3dC1pc3N1ZXIiLCJpZCI6IjY3NDU4NGMxZTgzNDAyMmMxYzA3M2ZjZCIsInVzZXJuYW1lIjoidGVzdGVyIiwiZXhwIjoxNzMzMTk4MDc2fQ.q6_f2N_rKWrcOtopisHpS-CImU-aS6I_AAAGHGzN-j4",
            tokenRepository.getToken().accessToken,
        )
        assertEquals(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImF1ZCI6Imp3dC1hdWRpZW5jZSIsImlzcyI6Imp3dC1pc3N1ZXIiLCJpZCI6IjY3NDU4NGMxZTgzNDAyMmMxYzA3M2ZjZCIsInVzZXJuYW1lIjoidGVzdGVyIiwiZXhwIjoxNzM1ODcyODc2fQ.zOMfZOpt67FOjkWXNsKDwr6puGsHtmTsbIE1eP4gJBc",
            tokenRepository.getToken().refreshToken,
        )
    }

    @Test
    fun signupAlreadyRegisteredErrorTest() = runTest {
        val authUseCase: AuthUseCase = SignupUseCase(
            defaultHttpRequest {
                respond(
                    content = ByteReadChannel(""""""),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val result = authUseCase(LoginParams("username", "password"))

        result as UseCase.Result.Error
        assertIs<AlreadyRegisteredException>(result.throwable)
    }

    @Test
    fun signupServerErrorTest() = runTest {
        val authUseCase = SignupUseCase(
            defaultHttpRequest {
                respond(
                    content = ByteReadChannel(""""""),
                    status = HttpStatusCode.InternalServerError,
                )
            },
        )

        val result = authUseCase(LoginParams("username", "password"))

        result as UseCase.Result.Error
        assertIs<ServerException>(result.throwable)
    }

    @Test
    fun signupSuccessTest() = runTest {
        val authUseCase = SignupUseCase(
            defaultHttpRequest { data ->
                respond(
                    content = ByteReadChannel(
                        """""".trimIndent(),
                    ),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val result = authUseCase(LoginParams("username", "password"))

        assertIs<UseCase.Result.Success<Boolean>>(result)
    }
}
