package io.github.youndie.mani

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.security.TokenService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

/**
 * Сервер, поднятый ровно так, как в бою, поверх настоящего mongod.
 *
 * Общая на все тесты `:server`. Раньше этот блок стоял отдельной копией в каждом тестовом классе:
 * их было три, а правка нужна была бы во всех сразу — и репозиторий показывал читателю ровно то,
 * чего показывать не должен.
 *
 * @param database своя база на прогон: тесты не мешают друг другу и не трогают чужие данные
 * @param overrides модули, объявленные после общих. Koin берёт последнее определение, поэтому так
 *   подменяется одна зависимость, а не собирается второй граф
 * @param block тело теста; параметром приходит адрес поднятого mongod — он нужен тем, кто
 *   проверяет не ответ сервера, а то, что осталось в базе
 */
internal fun maniTest(
    database: String = "mani-test",
    overrides: List<Module> = emptyList(),
    block: suspend ApplicationTestBuilder.(mongoUri: String) -> Unit,
) {
    val running = Mongod.instance().start(Version.V8_0_3)

    try {
        val address = running.current().serverAddress.toString()
        val config = ManiConfig(
            port = 0,
            mongo = MongoConfig(host = address, database = database),
            jwt = JWTConfig(),
            webRoot = null,
            development = false,
        )

        testApplication {
            application {
                configureManiPlugins(config)
                install(Koin) {
                    modules(listOf(coreModule(config), mongoStorageModule(config.mongo)) + overrides)
                }
                configureManiAuth(config, get<TokenService>())
                routing { maniApiRouting() }
            }

            block("mongodb://$address")
        }
    } finally {
        stopKoin()
        running.close()
    }
}

/**
 * Тела кодируются вручную: клиентский content-negotiation в зависимостях `:server` не нужен
 * нигде, кроме тестов, и тянуть его сюда ради нескольких запросов незачем.
 */
internal val maniJson = Json { ignoreUnknownKeys = true }

/** Заводит пользователя и возвращает его access-токен. */
internal suspend fun HttpClient.signIn(name: String, password: String): String {
    val credentials = maniJson.encodeToString(LoginParams.serializer(), LoginParams(name, password))

    val signup = post("/users") {
        contentType(ContentType.Application.Json)
        setBody(credentials)
    }

    val response = post("/auth") {
        contentType(ContentType.Application.Json)
        setBody(credentials)
    }
    // Отказ регистрации виден только здесь: без него вход отвечает 404, и тест читается как
    // «маршрута нет», хотя дело в пароле или занятом имени.
    assertEquals(
        HttpStatusCode.OK,
        response.status,
        "вход отвергнут; регистрация ответила ${signup.status}: ${signup.bodyAsText()}",
    )

    return maniJson.decodeFromString(Tokens.serializer(), response.bodyAsText()).accessToken
}

/** Разворачивает песочницу и возвращает её токены. */
internal suspend fun HttpClient.startSandbox(): Tokens {
    val response = post("/demo")
    assertEquals(HttpStatusCode.Created, response.status)

    return maniJson.decodeFromString(Tokens.serializer(), response.bodyAsText())
}

internal fun aTransaction(comment: String) = Transaction(
    id = "",
    amount = "10".toBigDecimal(),
    income = false,
    date = LocalDate.parse("2026-09-08"),
    until = null,
    period = Transaction.Period.OneTime,
    comment = comment,
    category = Category.default,
)

internal suspend fun HttpClient.postTransaction(token: String, body: Transaction): HttpResponse =
    post("/transactions") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(maniJson.encodeToString(Transaction.serializer(), body))
    }

internal suspend fun HttpClient.createTransaction(token: String, comment: String): Transaction {
    val response = postTransaction(token, aTransaction(comment))
    assertEquals(HttpStatusCode.Created, response.status)

    return maniJson.decodeFromString(Transaction.serializer(), response.bodyAsText())
}

internal suspend fun HttpClient.patchTransaction(token: String, path: String, body: Transaction): HttpResponse =
    patch("/transactions/$path") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(maniJson.encodeToString(Transaction.serializer(), body))
    }

internal suspend fun HttpClient.deleteTransaction(token: String, path: String): HttpResponse =
    delete("/transactions/$path") { bearerAuth(token) }

internal suspend fun HttpClient.transactions(token: String): List<Transaction> {
    val response = get("/transactions") { bearerAuth(token) }
    assertEquals(HttpStatusCode.OK, response.status)

    return maniJson.decodeFromString(response.bodyAsText())
}

internal suspend fun HttpClient.createCategory(token: String, name: String): Category {
    val response = post("/categories") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(maniJson.encodeToString(Category.serializer(), Category(id = "", name = name)))
    }
    assertEquals(HttpStatusCode.OK, response.status)

    return maniJson.decodeFromString(Category.serializer(), response.bodyAsText())
}

internal suspend fun HttpClient.patchCategory(token: String, path: String, body: Category): HttpResponse =
    patch("/categories/$path") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(maniJson.encodeToString(Category.serializer(), body))
    }

internal suspend fun HttpClient.categories(token: String): List<Category> {
    val response = get("/categories") { bearerAuth(token) }
    assertEquals(HttpStatusCode.OK, response.status)

    return maniJson.decodeFromString(response.bodyAsText())
}
