package io.github.youndie.mani

import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.auth.authRouting
import io.github.youndie.mani.feature.auth.data.AuthService
import io.github.youndie.mani.feature.auth.data.hashing.HashingService
import io.github.youndie.mani.feature.auth.data.hashing.Sha256HashingService
import io.github.youndie.mani.feature.category.categoryRouting
import io.github.youndie.mani.feature.currency.currencyRouting
import io.github.youndie.mani.feature.demo.data.DemoSandboxCleaner
import io.github.youndie.mani.feature.demo.data.DemoService
import io.github.youndie.mani.feature.demo.demoRouting
import io.github.youndie.mani.feature.health.healthRouting
import io.github.youndie.mani.feature.transaction.transactionRouting
import io.github.youndie.mani.feature.user.userRouting
import io.github.youndie.mani.security.TokenService
import io.github.youndie.mani.security.maniJwt
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.cancellation.CancellationException

/**
 * DI, общий для обеих сборок: конфигурация, токены, хеширование, логин.
 *
 * Хранилище сюда не входит — его модуль каждая сборка приносит свой. Всё остальное обязано быть
 * одним: разъехавшийся `TokenService` означал бы, что токен одной сборки не принимает другая.
 */
fun coreModule(config: ManiConfig): Module = module {
    single<ManiConfig> { config }
    single<JWTConfig> { config.jwt }
    single<MongoConfig> { config.mongo }
    single<TokenService> { TokenService(config.jwt) }
    single<HashingService> { Sha256HashingService() }
    single<AuthService> { AuthService(get(), get(), get()) }
    single<DemoSandboxCleaner> { DemoSandboxCleaner(get(), get()) }
    single<DemoService> { DemoService(get(), get(), get(), get(), get()) }
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

    // Что отвечать на то, чего маршрут не ждал.
    //
    // Без этого невалидный ObjectId в пути давал 500, а на нативной сборке ещё и молча: логгера
    // в общей части нет ни у одной сборки, и причина отказа не доезжала никуда. `CallLogging`
    // сюда не годится — он существует только на JVM.
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Отмена — не отказ. Запрос, от которого отказался клиент, не должен ни отвечать
            // 500, ни попадать в лог как ошибка сервера.
            if (cause is CancellationException) throw cause

            when (cause) {
                // Испорченный ввод: не разобранное тело, не разобранный параметр пути,
                // не-ObjectId там, где хранилище ждёт ObjectId. Это 400, и текст общий —
                // подробности отказа рассказывают об устройстве сервера больше, чем нужно.
                is BadRequestException,
                is IllegalArgumentException,
                is SerializationException,
                -> call.respond(HttpStatusCode.BadRequest, "Malformed request")

                else -> {
                    // `println`, а не логгер: его в общей части нет ни у одной сборки, а в
                    // контейнере stdout и есть лог. Без этой строки нативная сборка отвечала
                    // 500, не оставляя следа, по которому его можно объяснить.
                    println("mani: ${call.request.httpMethod.value} ${call.request.uri} — $cause")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
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
fun Application.configureManiAuth(config: ManiConfig, tokenService: TokenService) {
    install(Authentication) {
        maniJwt(config.jwt.name, tokenService)
    }
}

/** Маршруты API. Статику каждая сборка отдаёт по-своему и подключает сама. */
fun Routing.maniApiRouting() {
    authRouting()
    categoryRouting()
    demoRouting()
    healthRouting()
    currencyRouting()
    transactionRouting()
    userRouting()
}
