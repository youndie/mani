package io.github.youndie.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.reverse.TransitionWalker
import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.demo.DemoSeed
import io.github.youndie.mani.feature.demo.data.DEMO_USERNAME_PREFIX
import io.github.youndie.mani.feature.demo.data.DemoService
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.user.data.USER_COLLECTION
import io.github.youndie.mani.feature.user.data.UserDb
import io.github.youndie.mani.security.TokenService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Песочница проверяется по кругу: запрос без единого параметра должен вернуть рабочие токены,
 * под которыми уже лежат данные сида.
 *
 * Проверяется на JVM-сборке, но проверяется **общий** код: маршрут, сервис и сид лежат в
 * `:shared` и `:server-common`, а `:server` приносит сюда только хранилище. Нативная сборка
 * получает ровно то же самое — если бы песочница потребовала своей реализации на той стороне,
 * это было бы видно здесь как невозможность собрать тест из общих типов.
 */
class DemoRoutingTest {
    private lateinit var running: TransitionWalker.ReachedState<RunningMongodProcess>

    private val config
        get() =
            ManiConfig(
                port = 0,
                mongo = MongoConfig(host = running.current().serverAddress.toString(), database = "demo-test"),
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

    /**
     * @param overrides модули, объявленные после общих: Koin берёт последнее определение, и так
     *   тест подменяет одну зависимость, не собирая граф заново
     */
    private fun demoTest(overrides: List<Module> = emptyList(), block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val maniConfig = config
            application {
                configureManiPlugins(maniConfig)
                install(Koin) {
                    modules(listOf(coreModule(maniConfig), mongoStorageModule(maniConfig.mongo)) + overrides)
                }
                configureManiAuth(maniConfig, get<TokenService>())
                routing { maniApiRouting() }
            }
            block()
        }

    /**
     * Тело разбирается сериализатором вручную: клиентский content-negotiation в зависимостях
     * `:server` не нужен нигде, кроме этого теста, и тянуть его сюда ради трёх запросов незачем.
     */
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpResponse.tokens(): Tokens {
        assertEquals(HttpStatusCode.Created, status)
        return json.decodeFromString(bodyAsText())
    }

    private suspend fun HttpClient.transactions(accessToken: String): List<Transaction> {
        val response = get("/transactions") { bearerAuth(accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `sandbox request returns working tokens`() = demoTest {
        val client = createClient { }
        val tokens = client.post("/demo").tokens()

        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())

        val transactions = client.transactions(tokens.accessToken)

        assertEquals(DemoSeed.rules.size, transactions.size)
        assertEquals(
            DemoSeed.rules.map { it.comment }.toSet(),
            transactions.map { it.comment }.toSet(),
        )
    }

    @Test
    fun `seeded transactions carry real categories`() = demoTest {
        val client = createClient { }
        val tokens = client.post("/demo").tokens()

        val transactions = client.transactions(tokens.accessToken)

        // Категория, не найденная у владельца, молча подменяется на `Category.default`:
        // ничего не падает, а песочница выглядит так, будто категорий в продукте нет.
        assertTrue(
            transactions.none { it.category == Category.default },
            "категории сида не доехали до транзакций",
        )
        assertEquals(
            DemoSeed.categories.toSet(),
            transactions.map { it.category.name }.toSet(),
        )
    }

    @Test
    fun `two visitors get separate sandboxes`() = demoTest {
        val client = createClient { }

        val first = client.post("/demo").tokens()
        val second = client.post("/demo").tokens()

        assertNotEquals(first.accessToken, second.accessToken)

        // Ради этого всё и затевалось: витрина на общем аккаунте позволяла любому посетителю
        // править и удалять чужие данные.
        assertEquals(DemoSeed.rules.size, client.transactions(second.accessToken).size)
    }

    /**
     * Витрина не бесконечна.
     *
     * `POST /demo` не требует ни ввода, ни входа, а база стенда живёт на 256 МиБ: цикл запросов
     * заводил пользователя с семью правилами столько раз, сколько успеет. Уборка от этого не
     * спасает — она уносит то, чему больше суток.
     *
     * Потолок здесь занижен подменой зависимости: проверять его настоящим значением означало бы
     * завести пятьсот песочниц, то есть измерять терпение прогона, а не правило.
     */
    @Test
    fun `a full demo answers 503 and creates nobody`() {
        val small = module {
            single { DemoService(get(), get(), get(), get(), get(), maxLiveSandboxes = 2) }
        }

        demoTest(overrides = listOf(small)) {
            val client = createClient { }

            client.post("/demo").tokens()
            client.post("/demo").tokens()

            val refused = client.post("/demo")
            assertEquals(HttpStatusCode.ServiceUnavailable, refused.status)

            // Третьей песочницы нет: отказ по потолку не должен оставлять за собой половину
            // заведённого пользователя.
            assertEquals(2, users().count { it.startsWith(DEMO_USERNAME_PREFIX) })
        }
    }

    /** Имена песочниц, как их видит база. */
    private suspend fun users(): List<String> {
        val client = MongoClient.create("mongodb://${running.current().serverAddress}")
        return client.use {
            it
                .getDatabase("demo-test")
                .getCollection<UserDb>(USER_COLLECTION)
                .find<UserDb>()
                .toList()
                .map(UserDb::username)
        }
    }
}
