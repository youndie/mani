package ru.workinprogress.mani

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.RefreshParams
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Приёмка нативной сборки целиком: HTTP, JSON, DI, выдача и проверка токена, Mongo.
 *
 * Проверяется тем же кодом, что пойдёт в образ (`maniModule`), и против настоящего mongod.
 * Часть этой цепочки иначе не проверяется ничем: провайдер криптографии, например, отсутствует
 * не на сборке, а на первом логине — то есть отказ выглядел бы как «сервер поднялся и молчит».
 */
class ManiApiTest {
    private fun withMani(block: suspend ApiScope.() -> Unit) {
        val config = TestMongo.config().let { it.copy(mongo = it.mongo.copy(database = TestMongo.uniqueDatabaseName("api"))) }

        testApplication {
            application { maniModule(config) }

            val http =
                createClient {
                    install(ContentNegotiation) { json(Json { isLenient = true; ignoreUnknownKeys = true }) }
                }

            try {
                ApiScope(http).block()
            } finally {
                // База теста уносится за собой: прогонов много, а mongod один.
                TestMongo.client().use { it.getDatabase(config.mongo.database).drop() }
            }
        }
    }

    class ApiScope(val http: io.ktor.client.HttpClient) {
        lateinit var tokens: Tokens

        suspend fun register(
            name: String,
            password: String,
        ): HttpResponse =
            http.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(LoginParams(name, password))
            }

        suspend fun login(
            name: String,
            password: String,
        ): HttpResponse =
            http.post("/auth") {
                contentType(ContentType.Application.Json)
                setBody(LoginParams(name, password))
            }
    }

    @Test
    // Запятой в имени быть не может: Kotlin/Native отвергает её на компиляции теста.
    fun `register then login then use the token`() =
        runBlocking {
            withMani {
                assertEquals(HttpStatusCode.Created, register("vasya", "hunter2").status)

                // Повторная регистрация того же имени — 400, а не второй пользователь.
                assertEquals(HttpStatusCode.BadRequest, register("vasya", "hunter2").status)

                // Неверный пароль отвергается: значит хеш действительно проверяется, а не
                // принимается на слово.
                assertEquals(HttpStatusCode.NotFound, login("vasya", "wrong").status)

                val response = login("vasya", "hunter2")
                assertEquals(HttpStatusCode.OK, response.status)
                tokens = response.body()

                assertTrue(tokens.accessToken.count { it == '.' } == 2, "access-токен — это JWT")

                val categories = http.get("/categories") { header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}") }
                assertEquals(HttpStatusCode.OK, categories.status)
                assertEquals(emptyList(), categories.body<List<Category>>())
            }
        }

    @Test
    fun `protected routes require a valid token`() =
        runBlocking {
            withMani {
                assertEquals(HttpStatusCode.Unauthorized, http.get("/transactions").status)

                val garbage =
                    http.get("/transactions") {
                        header(HttpHeaders.Authorization, "Bearer not-a-token")
                    }
                assertEquals(HttpStatusCode.Unauthorized, garbage.status, "мусорный токен — 401, а не 500")
            }
        }

    @Test
    fun `transaction keeps its category`() =
        runBlocking {
            withMani {
                register("cats", "pass")
                tokens = login("cats", "pass").body()
                val auth = "Bearer ${tokens.accessToken}"

                val category: Category =
                    http
                        .post("/categories") {
                            header(HttpHeaders.Authorization, auth)
                            contentType(ContentType.Application.Json)
                            setBody(Category("", "Еда"))
                        }.body()

                val created: Transaction =
                    http
                        .post("/transactions") {
                            header(HttpHeaders.Authorization, auth)
                            contentType(ContentType.Application.Json)
                            setBody(
                                Transaction(
                                    id = "",
                                    amount = "1234.56".toBigDecimal(),
                                    income = false,
                                    date = LocalDate.parse("2026-08-11"),
                                    until = null,
                                    period = Transaction.Period.OneTime,
                                    comment = "обед",
                                    category = category,
                                ),
                            )
                        }.body()

                // Категория подставляется маршрутом; её потеря выглядела бы как исправная
                // работа с «Default» вместо имени.
                assertEquals("Еда", created.category.name)
                assertEquals(category.id, created.category.id)
                assertEquals("1234.56", created.amount.toPlainString())

                val all: List<Transaction> =
                    http.get("/transactions") { header(HttpHeaders.Authorization, auth) }.body()

                assertEquals(1, all.size)
                assertEquals("Еда", all.single().category.name)
            }
        }

    @Test
    fun `refresh returns a new pair and burns the old token`() =
        runBlocking {
            withMani {
                register("refresher", "pass")
                tokens = login("refresher", "pass").body()

                val refreshed =
                    http.post("/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshParams(tokens.refreshToken))
                    }
                assertEquals(HttpStatusCode.OK, refreshed.status)
                val next: Tokens = refreshed.body()
                assertTrue(next.refreshToken != tokens.refreshToken)

                // Использованный refresh-токен больше не работает: подписи мало, он обязан
                // лежать в базе.
                val reused =
                    http.post("/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshParams(tokens.refreshToken))
                    }
                assertEquals(HttpStatusCode.Unauthorized, reused.status)
            }
        }
}
