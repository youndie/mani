package io.github.youndie.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.github.youndie.mani.feature.demo.DemoSeed
import io.github.youndie.mani.feature.demo.data.DEMO_USERNAME_PREFIX
import io.github.youndie.mani.feature.demo.data.DemoService
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.user.data.USER_COLLECTION
import io.github.youndie.mani.feature.user.data.UserDb
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import org.koin.dsl.module
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

    @Test
    fun `sandbox request returns working tokens`() = maniTest {
        val client = createClient { }
        val tokens = client.startSandbox()

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
    fun `seeded transactions carry real categories`() = maniTest {
        val client = createClient { }
        val tokens = client.startSandbox()

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
    fun `two visitors get separate sandboxes`() = maniTest {
        val client = createClient { }

        val first = client.startSandbox()
        val second = client.startSandbox()

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

        maniTest(database = DATABASE, overrides = listOf(small)) { mongoUri ->
            val client = createClient { }

            client.startSandbox()
            client.startSandbox()

            assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/demo").status)

            // Третьей песочницы нет: отказ по потолку не должен оставлять за собой половину
            // заведённого пользователя.
            assertEquals(2, sandboxNames(mongoUri).size)
        }
    }

    /** Имена песочниц, как их видит база: ответ сервера о том, чего он не создал, молчит. */
    private suspend fun sandboxNames(mongoUri: String): List<String> = MongoClient
        .create(mongoUri)
        .use { client ->
            client
                .getDatabase(DATABASE)
                .getCollection<UserDb>(USER_COLLECTION)
                .find<UserDb>()
                .toList()
                .map(UserDb::username)
                .filter { it.startsWith(DEMO_USERNAME_PREFIX) }
        }

    private companion object {
        const val DATABASE = "demo-test"
    }

    /**
     * Засев СВОЕГО аккаунта — не то же, что песочница.
     *
     * Песочница заводит нового пользователя и сама выдаёт первый токен; здесь пользователь уже
     * есть, и данные должны лечь ему. Отсюда и проверка токена на маршруте: без неё любой
     * прохожий наполнял бы чужой аккаунт.
     */
    @Test
    fun `seeding fills the caller's own account`() = maniTest {
        val client = createClient { }
        val token = client.signIn("emptyhanded", "hunter22")

        assertEquals(emptyList(), client.transactions(token))

        val seeded = client.post("/demo/seed") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, seeded.status)

        val rules = client.transactions(token)
        assertEquals(DemoSeed.rules.size, rules.size)
        assertEquals(DemoSeed.rules.map { it.comment }.toSet(), rules.map { it.comment }.toSet())

        // И категории настоящие, а не подменённые умолчанием.
        assertTrue(rules.none { it.category == Category.default }, "категории сида не доехали")
    }

    /** Без токена засевать нечего: маршрут стоит под проверкой, в отличие от `POST /demo`. */
    @Test
    fun `seeding without a token is refused`() = maniTest {
        val client = createClient { }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/demo/seed").status)
    }
}
