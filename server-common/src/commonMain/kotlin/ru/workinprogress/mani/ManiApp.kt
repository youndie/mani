package ru.workinprogress.mani

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.resources.Resources
import io.ktor.server.routing.Routing
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.workinprogress.feature.auth.authRouting
import ru.workinprogress.feature.auth.data.AuthService
import ru.workinprogress.feature.auth.data.hashing.HashingService
import ru.workinprogress.feature.auth.data.hashing.Sha256HashingService
import ru.workinprogress.feature.category.categoryRouting
import ru.workinprogress.feature.currency.currencyRouting
import ru.workinprogress.feature.demo.data.DemoService
import ru.workinprogress.feature.demo.demoRouting
import ru.workinprogress.feature.transaction.transactionRouting
import ru.workinprogress.feature.user.userRouting
import ru.workinprogress.mani.config.JWTConfig
import ru.workinprogress.mani.config.ManiConfig
import ru.workinprogress.mani.config.MongoConfig
import ru.workinprogress.mani.security.TokenService
import ru.workinprogress.mani.security.maniJwt

/**
 * DI, общий для обеих сборок: конфигурация, токены, хеширование, логин.
 *
 * Хранилище сюда не входит — его модуль каждая сборка приносит свой. Всё остальное обязано быть
 * одним: разъехавшийся `TokenService` означал бы, что токен одной сборки не принимает другая.
 */
fun coreModule(config: ManiConfig): Module =
    module {
        single<ManiConfig> { config }
        single<JWTConfig> { config.jwt }
        single<MongoConfig> { config.mongo }
        single<TokenService> { TokenService(config.jwt) }
        single<HashingService> { Sha256HashingService() }
        single<AuthService> { AuthService(get(), get(), get()) }
        single<DemoService> { DemoService(get(), get(), get(), get()) }
    }

/**
 * Плагины, одинаковые для обеих сборок.
 *
 * CORS ставится только в режиме разработки — так было и на JVM: в стенде фронтенд отдаёт тот же
 * сервер, и разрешать чужие источники незачем.
 */
fun Application.configureManiPlugins(config: ManiConfig) {
    if (config.development) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            allowHeader(HttpHeaders.ContentType)
            allowHeadersPrefixed("sec-")
            allowHeader(HttpHeaders.Authorization)
            exposeHeader(HttpHeaders.Authorization)
            anyHost()
        }
    }

    install(Resources)
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
            },
        )
    }
}

/**
 * Проверка токенов. Ставится отдельным вызовом после Koin: [TokenService] берётся из графа, а не
 * собирается заново, — иначе секрет пришлось бы протаскивать во второе место.
 */
fun Application.configureManiAuth(
    config: ManiConfig,
    tokenService: TokenService,
) {
    install(Authentication) {
        maniJwt(config.jwt.name, tokenService)
    }
}

/** Маршруты API. Статику каждая сборка отдаёт по-своему и подключает сама. */
fun Routing.maniApiRouting() {
    authRouting()
    categoryRouting()
    demoRouting()
    currencyRouting()
    transactionRouting()
    userRouting()
}
