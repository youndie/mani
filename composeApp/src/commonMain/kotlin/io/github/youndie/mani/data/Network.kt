package io.github.youndie.mani.data

import io.github.youndie.mani.data.serverConfig
import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.host
import io.ktor.client.request.port
import io.ktor.client.request.url
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {
    single<HttpClient> {
        HttpClient {
            install(Resources)
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        println("HTTP Client: $message")
                    }
                }
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            install(Auth) {
                bearer {
                    realm = serverConfig.host

                    loadTokens {
                        get<TokenRepository>().getToken()
                    }
                    refreshTokens { refreshSession(get(), get()) { markAsRefreshTokenRequest() } }
                }
            }
            defaultRequest {
                contentType(Json)
                url {
                    protocol = if (serverConfig.scheme == "http") URLProtocol.HTTP else URLProtocol.HTTPS
                    host = serverConfig.host
                    serverConfig.port?.toIntOrNull()?.let {
                        port = it
                    }
                }
            }
        }
    }
}
