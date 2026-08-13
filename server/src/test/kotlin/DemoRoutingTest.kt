package ru.workinprogress.mani

import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.reverse.TransitionWalker
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.koin.core.context.stopKoin
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.mani.config.JWTConfig
import ru.workinprogress.mani.config.ManiConfig
import ru.workinprogress.mani.config.MongoConfig
import ru.workinprogress.mani.demo.DemoSeed
import ru.workinprogress.mani.security.TokenService
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

    private fun demoTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
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
    fun `sandbox request returns working tokens`() =
        demoTest {
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
    fun `seeded transactions carry real categories`() =
        demoTest {
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
    fun `two visitors get separate sandboxes`() =
        demoTest {
            val client = createClient { }

            val first = client.post("/demo").tokens()
            val second = client.post("/demo").tokens()

            assertNotEquals(first.accessToken, second.accessToken)

            // Ради этого всё и затевалось: витрина на общем аккаунте позволяла любому посетителю
            // править и удалять чужие данные.
            assertEquals(DemoSeed.rules.size, client.transactions(second.accessToken).size)
        }
}
