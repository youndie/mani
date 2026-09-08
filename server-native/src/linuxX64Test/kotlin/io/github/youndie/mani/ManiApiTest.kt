package io.github.youndie.mani

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.RefreshParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
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
        val config = TestMongo.config().let {
            it.copy(mongo = it.mongo.copy(database = TestMongo.uniqueDatabaseName("api")))
        }

        testApplication {
            application { maniModule(config) }

            val http =
                createClient {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                isLenient = true
                                ignoreUnknownKeys = true
                            },
                        )
                    }
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

        suspend fun register(name: String, password: String): HttpResponse = http.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(LoginParams(name, password))
        }

        suspend fun login(name: String, password: String): HttpResponse = http.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody(LoginParams(name, password))
        }

        /** Заводит пользователя и возвращает готовый заголовок — тестам про двоих нужен именно он. */
        suspend fun signIn(name: String, password: String): String {
            register(name, password)
            val issued: Tokens = login(name, password).body()
            return "Bearer ${issued.accessToken}"
        }

        suspend fun createTransaction(auth: String, comment: String): Transaction = http
            .post("/transactions") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(
                    Transaction(
                        id = "",
                        amount = "10".toBigDecimal(),
                        income = false,
                        date = LocalDate.parse("2026-09-08"),
                        until = null,
                        period = Transaction.Period.OneTime,
                        comment = comment,
                        category = Category.default,
                    ),
                )
            }.body()

        suspend fun postTransaction(auth: String, body: Transaction): HttpResponse = http
            .post("/transactions") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        suspend fun patchTransaction(auth: String, path: String, body: Transaction): HttpResponse = http
            .patch("/transactions/$path") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        suspend fun transactions(auth: String): List<Transaction> = http
            .get("/transactions") { header(HttpHeaders.Authorization, auth) }
            .body()

        suspend fun createCategory(auth: String, name: String): Category = http
            .post("/categories") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(Category(id = "", name = name))
            }.body()

        suspend fun patchCategory(auth: String, path: String, body: Category): HttpResponse = http
            .patch("/categories/$path") {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        suspend fun categories(auth: String): List<Category> = http
            .get("/categories") { header(HttpHeaders.Authorization, auth) }
            .body()
    }

    // Запятой в имени быть не может: Kotlin/Native отвергает её на компиляции теста.
    @Test
    fun `register then login then use the token`() = runBlocking {
        withMani {
            assertEquals(HttpStatusCode.Created, register("vasya", "hunter22").status)

            // Повторная регистрация того же имени — 400, а не второй пользователь.
            assertEquals(HttpStatusCode.BadRequest, register("vasya", "hunter22").status)

            // Неверный пароль отвергается: значит хеш действительно проверяется, а не
            // принимается на слово.
            assertEquals(HttpStatusCode.NotFound, login("vasya", "wrong").status)

            val response = login("vasya", "hunter22")
            assertEquals(HttpStatusCode.OK, response.status)
            tokens = response.body()

            assertTrue(tokens.accessToken.count { it == '.' } == 2, "access-токен — это JWT")

            val categories = http.get("/categories") {
                header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            }
            assertEquals(HttpStatusCode.OK, categories.status)
            assertEquals(emptyList(), categories.body<List<Category>>())
        }
    }

    @Test
    fun `protected routes require a valid token`() = runBlocking {
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
    fun `transaction keeps its category`() = runBlocking {
        withMani {
            register("cats", "password")
            tokens = login("cats", "password").body()
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
    fun `refresh returns a new pair and burns the old token`() = runBlocking {
        withMani {
            register("refresher", "password")
            tokens = login("refresher", "password").body()

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

    /**
     * Идентификатор правится по пути, а не по телу.
     *
     * Проверка владельца смотрела на `path.id`, а документ на запись выбирался по `id` из тела:
     * достаточно было отправить `PATCH` на СВОЮ запись, приложив в теле чужую, — и чужая
     * переписывалась, заодно меняя владельца на вызывающего. Ответ при этом был 200, то есть
     * снаружи всё выглядело исправным.
     */
    @Test
    fun `a stranger cannot patch a foreign transaction through the id in the body`() = runBlocking {
        withMani {
            val owner = signIn("owner", "hunter22")
            val stranger = signIn("stranger", "hunter22")

            val theirs = createTransaction(owner, "theirs")
            val mine = createTransaction(stranger, "mine")

            // В лоб: чужой идентификатор в пути. Это было закрыто и раньше.
            val direct = patchTransaction(stranger, path = theirs.id, body = theirs.copy(comment = "stolen"))
            assertEquals(HttpStatusCode.Forbidden, direct.status)

            // Обходом: свой идентификатор в пути, чужой — в теле.
            val smuggled = patchTransaction(stranger, path = mine.id, body = theirs.copy(comment = "stolen"))
            assertEquals(HttpStatusCode.OK, smuggled.status)

            val ownersNow = transactions(owner)
            assertEquals(1, ownersNow.size, "чужая запись сменила владельца")
            assertEquals("theirs", ownersNow.single().comment, "чужая запись переписана")

            // А своя запись правится: путь и решает, что именно пишется.
            assertEquals(listOf("stolen"), transactions(stranger).map { it.comment })
        }
    }

    /**
     * То же самое у категорий, и потому отдельным случаем: маршрут другой, хранилище другое,
     * а ошибка одна — принадлежность проверялась по пути, переименовывалось названное телом.
     */
    @Test
    fun `a stranger cannot rename a foreign category through the id in the body`() = runBlocking {
        withMani {
            val owner = signIn("owner", "hunter22")
            val stranger = signIn("stranger", "hunter22")

            val theirs = createCategory(owner, "Food")
            val mine = createCategory(stranger, "Mine")

            val direct = patchCategory(stranger, path = theirs.id, body = theirs.copy(name = "stolen"))
            assertEquals(HttpStatusCode.Forbidden, direct.status)

            val smuggled = patchCategory(stranger, path = mine.id, body = theirs.copy(name = "stolen"))
            assertEquals(HttpStatusCode.OK, smuggled.status)

            assertEquals(listOf("Food"), categories(owner).map { it.name }, "чужая категория переименована")
            assertEquals(listOf("stolen"), categories(stranger).map { it.name })
        }
    }

    /**
     * Вид токена проверяется и на границе API, а не только внутри `TokenService`.
     *
     * Проводка тут общая с JVM-сборкой (`ManiJwtProvider` и `AuthService` живут в
     * `:server-common`), а сам разбор claim'ов проверяется на обеих сборках в `TokenServiceTest`.
     */
    @Test
    fun `a refresh token opens no door and an access token refreshes nothing`() = runBlocking {
        withMani {
            register("kinds", "hunter22")
            val pair: Tokens = login("kinds", "hunter22").body()

            // Refresh живёт месяц. Пока вид не проверялся, он открывал любой маршрут — то есть
            // час жизни access-токена не значил ничего.
            val withRefresh =
                http.get("/transactions") {
                    header(HttpHeaders.Authorization, "Bearer ${pair.refreshToken}")
                }
            assertEquals(HttpStatusCode.Unauthorized, withRefresh.status)

            // И обратно: access-токеном сессию не продлить.
            val refreshedWithAccess =
                http.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshParams(pair.accessToken))
                }
            assertEquals(HttpStatusCode.Unauthorized, refreshedWithAccess.status)

            // По назначению работают оба.
            val withAccess =
                http.get("/transactions") {
                    header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
                }
            assertEquals(HttpStatusCode.OK, withAccess.status)

            val refreshed =
                http.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshParams(pair.refreshToken))
                }
            assertEquals(HttpStatusCode.OK, refreshed.status)
        }
    }

    /**
     * Испорченный ввод — это 400, а не 500.
     *
     * `not-an-id` доходит до хранилища и падает там: на JVM его отвергает конструктор
     * `ObjectId`, на нативной сборке — сериализатор поля. Отказ у двух реализаций разный, и
     * потому проверяется в каждой; общая здесь только раскладка исключения в ответ.
     */
    @Test
    fun `a malformed id in the path is a bad request`() = runBlocking {
        withMani {
            val auth = signIn("malformed", "hunter22")

            val deleted = http.delete("/transactions/not-an-id") { header(HttpHeaders.Authorization, auth) }
            assertEquals(HttpStatusCode.BadRequest, deleted.status)

            // Тело, которое не разбирается, Ktor отвергал сам и до `StatusPages`. Но общий
            // обработчик ловит `Throwable`, а значит перехватывает и его: без ветки
            // `BadRequestException` ответ стал бы 500. Сторожится это, а не поведение Ktor.
            val posted =
                http.post("/transactions") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody("{\"amount\": ")
                }
            assertEquals(HttpStatusCode.BadRequest, posted.status)
        }
    }

    /**
     * Маршрут регистрации действительно зовёт проверку, а не просто имеет её рядом.
     *
     * Границы разобраны в `CredentialsTest`; здесь важно другое — что отказ доезжает до ответа
     * вместе с текстом и что пользователь при этом не заводится.
     */
    @Test
    fun `registration refuses credentials it cannot accept`() = runBlocking {
        withMani {
            val refused = register("vasya", "short")

            assertEquals(HttpStatusCode.BadRequest, refused.status)
            assertEquals("Password must be at least 8 characters long", refused.bodyAsText())

            // И пользователя нет: отказ по форме не должен оставлять следа в базе.
            assertEquals(HttpStatusCode.NotFound, login("vasya", "short").status)

            assertEquals(HttpStatusCode.BadRequest, register("", "").status)
            assertEquals(HttpStatusCode.BadRequest, register("demo-1a2b3c4d", "hunter22").status)
        }
    }

    /**
     * Маршруты зовут проверку правила, а не просто имеют её рядом.
     *
     * Границы разобраны в `RulesTest`; здесь важно, что отказ доезжает до ответа и что запись
     * при этом не заводится. Форма клиента половину этого не допускает — но форма это удобство,
     * а не граница: за ней открытый HTTP.
     */
    @Test
    fun `a rule the product cannot honour is refused`() = runBlocking {
        withMani {
            val auth = signIn("rules", "hunter22")

            val sound =
                Transaction(
                    id = "",
                    amount = "10".toBigDecimal(),
                    income = false,
                    date = LocalDate.parse("2026-09-08"),
                    until = null,
                    period = Transaction.Period.Month,
                    comment = "Rent",
                    category = Category.default,
                )

            val zero = postTransaction(auth, sound.copy(amount = "0".toBigDecimal()))
            assertEquals(HttpStatusCode.BadRequest, zero.status)
            assertEquals("Amount must be greater than zero", zero.bodyAsText())

            val backwards = postTransaction(auth, sound.copy(until = LocalDate.parse("2026-09-07")))
            assertEquals(HttpStatusCode.BadRequest, backwards.status)

            val nameless =
                http.post("/categories") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(Category(id = "", name = " "))
                }
            assertEquals(HttpStatusCode.BadRequest, nameless.status)

            // Ничего из отвергнутого не осело в базе.
            assertEquals(emptyList(), transactions(auth))
            assertEquals(emptyList(), categories(auth))
        }
    }
}
