package io.github.youndie.mani

import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.reverse.TransitionWalker
import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.security.TokenService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Испорченный ввод — это 400, и на JVM-сборке тоже.
 *
 * Раскладка исключения в ответ общая (`configureManiPlugins`), а вот САМО исключение у двух
 * реализаций хранилища разное: `not-an-id` здесь отвергает конструктор `ObjectId`, на нативной
 * сборке — сериализатор поля. Поэтому случай проверяется в каждой сборке своим тестом; парный
 * живёт в `ManiApiTest` в `:server-native`.
 */
class MalformedRequestTest {
    private lateinit var running: TransitionWalker.ReachedState<RunningMongodProcess>

    private val config
        get() =
            ManiConfig(
                port = 0,
                mongo = MongoConfig(host = running.current().serverAddress.toString(), database = "malformed-test"),
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

    private fun malformedTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
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

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.signIn(name: String, password: String): String {
        val credentials = json.encodeToString(LoginParams.serializer(), LoginParams(name, password))

        post("/users") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }

        val response = post("/auth") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }

        return json.decodeFromString(Tokens.serializer(), response.bodyAsText()).accessToken
    }

    @Test
    fun `a malformed id in the path is a bad request`() = malformedTest {
        val client = createClient { }
        val token = client.signIn("malformed", "hunter22")

        val response = client.delete("/transactions/not-an-id") { bearerAuth(token) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    /**
     * Тело, которое не разбирается, — тоже 400, и проверка эта не холостая.
     *
     * Ktor отвечал так и до `StatusPages`: `BadRequestException` он раскладывает сам. Но
     * обработчик выше ловит `Throwable`, то есть перехватывает и его тоже, — и стоит убрать
     * из `when` ветку `BadRequestException`, как ответ становится 500. Сторожится именно это:
     * не поведение Ktor, а то, что общий обработчик его не испортил.
     */
    @Test
    fun `an unparseable body is a bad request`() = malformedTest {
        val client = createClient { }
        val token = client.signIn("malformed", "hunter22")

        val response = client.post("/transactions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{\"amount\": ")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
