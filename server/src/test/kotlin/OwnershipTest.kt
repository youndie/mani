package io.github.youndie.mani

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.reverse.TransitionWalker
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
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Одна запись — один владелец, проверено на JVM-сборке.
 *
 * Те же случаи проверяет `ManiApiTest` в `:server-native`, и это не дублирование: маршрут общий,
 * а фильтр записи свой у каждой реализации хранилища. Дыра жила именно в паре «проверка по пути,
 * запись по телу», то есть ровно на стыке общего кода с реализацией, и одной проверки на одну
 * сборку недостаточно — вторая реализация может починиться, а первая остаться.
 *
 * Транзакции и категории лежат здесь вместе, потому что это одна и та же ошибка в двух местах:
 * проверка смотрит на путь, запись слушает тело.
 */
class OwnershipTest {
    private lateinit var running: TransitionWalker.ReachedState<RunningMongodProcess>

    private val config
        get() =
            ManiConfig(
                port = 0,
                mongo = MongoConfig(host = running.current().serverAddress.toString(), database = "ownership-test"),
                jwt = JWTConfig(),
                webRoot = null,
                development = false,
            )

    @BeforeTest
    fun setUp() {
        running = Mongod.instance().start(Version.V8_0_3)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        running.close()
    }

    private fun ownershipTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val maniConfig = config
        application {
            configureManiPlugins(maniConfig)
            install(Koin) {
                modules(coreModule(maniConfig), mongoStorageModule(maniConfig.mongo))
            }
            configureManiAuth(maniConfig, get<TokenService>())
            routing { maniApiRouting() }
        }
        block()
    }

    /** Тела кодируются вручную: клиентский content-negotiation в зависимостях `:server` не нужен. */
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.signIn(name: String, password: String): String {
        val credentials = json.encodeToString(LoginParams.serializer(), LoginParams(name, password))

        assertEquals(
            HttpStatusCode.Created,
            post("/users") {
                contentType(ContentType.Application.Json)
                setBody(credentials)
            }.status,
        )

        val response = post("/auth") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        assertEquals(HttpStatusCode.OK, response.status)

        return json.decodeFromString(Tokens.serializer(), response.bodyAsText()).accessToken
    }

    private suspend fun HttpClient.createTransaction(token: String, comment: String): Transaction {
        val response = post("/transactions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    Transaction.serializer(),
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
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)

        return json.decodeFromString(Transaction.serializer(), response.bodyAsText())
    }

    private suspend fun HttpClient.patchTransaction(token: String, path: String, body: Transaction): HttpResponse =
        patch("/transactions/$path") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Transaction.serializer(), body))
        }

    private suspend fun HttpClient.transactions(token: String): List<Transaction> {
        val response = get("/transactions") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.createCategory(token: String, name: String): Category {
        val response = post("/categories") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Category.serializer(), Category(id = "", name = name)))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        return json.decodeFromString(Category.serializer(), response.bodyAsText())
    }

    private suspend fun HttpClient.patchCategory(token: String, path: String, body: Category): HttpResponse =
        patch("/categories/$path") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Category.serializer(), body))
        }

    private suspend fun HttpClient.categories(token: String): List<Category> {
        val response = get("/categories") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
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
    fun `a stranger cannot patch a foreign transaction through the id in the body`() = ownershipTest {
        val client = createClient { }

        val owner = client.signIn("owner", "hunter22")
        val stranger = client.signIn("stranger", "hunter22")

        val theirs = client.createTransaction(owner, "theirs")
        val mine = client.createTransaction(stranger, "mine")

        // В лоб: чужой идентификатор в пути. Это было закрыто и раньше.
        val direct = client.patchTransaction(stranger, path = theirs.id, body = theirs.copy(comment = "stolen"))
        assertEquals(HttpStatusCode.Forbidden, direct.status)

        // Обходом: свой идентификатор в пути, чужой — в теле.
        val smuggled = client.patchTransaction(stranger, path = mine.id, body = theirs.copy(comment = "stolen"))
        assertEquals(HttpStatusCode.OK, smuggled.status)

        val ownersNow = client.transactions(owner)
        assertEquals(1, ownersNow.size, "чужая запись сменила владельца")
        assertEquals("theirs", ownersNow.single().comment, "чужая запись переписана")

        // А своя запись правится: путь и решает, что именно пишется.
        assertEquals(listOf("stolen"), client.transactions(stranger).map { it.comment })
    }

    /**
     * То же самое у категорий, и потому отдельным случаем: маршрут другой, хранилище другое,
     * а ошибка одна — принадлежность проверялась по пути, переименовывалось названное телом.
     */
    @Test
    fun `a stranger cannot rename a foreign category through the id in the body`() = ownershipTest {
        val client = createClient { }

        val owner = client.signIn("owner", "hunter22")
        val stranger = client.signIn("stranger", "hunter22")

        val theirs = client.createCategory(owner, "Food")
        val mine = client.createCategory(stranger, "Mine")

        val direct = client.patchCategory(stranger, path = theirs.id, body = theirs.copy(name = "stolen"))
        assertEquals(HttpStatusCode.Forbidden, direct.status)

        val smuggled = client.patchCategory(stranger, path = mine.id, body = theirs.copy(name = "stolen"))
        assertEquals(HttpStatusCode.OK, smuggled.status)

        assertEquals(listOf("Food"), client.categories(owner).map { it.name }, "чужая категория переименована")
        assertEquals(listOf("stolen"), client.categories(stranger).map { it.name })
    }
}
