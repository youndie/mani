package io.github.youndie.mani.data

import io.github.youndie.mani.data.serverConfig
import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.*
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
