package ru.workinprogress.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import ru.workinprogress.feature.auth.data.AuthService
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.data.TokenRepository
import ru.workinprogress.feature.user.data.UserRepository
import ru.workinprogress.mani.config.JWTConfig
import ru.workinprogress.mani.config.ManiConfig
import ru.workinprogress.mani.config.MongoConfig
import ru.workinprogress.mani.security.TokenService
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Граф JVM-сборки собирается целиком.
 *
 * Зависимости **создаются**, а не проверяются рефлексией: `verify()` из koin-test обходит
 * конструкторы и спотыкается о `ManiConfig`, который кладётся в граф готовым объектом, а не
 * собирается Koin'ом. Создание к тому же строже — оно ловит и опечатку в привязке порта.
 *
 * Подключения к Mongo здесь не возникает: драйвер соединяется лениво, на первой операции.
 *
 * У нативной сборки свой такой тест — модули хранилища у сборок разные.
 */
class ServerKoinModuleTest {
    private val config =
        ManiConfig(
            port = 8080,
            mongo = MongoConfig(host = "localhost"),
            jwt = JWTConfig(),
            webRoot = null,
            development = false,
        )

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun checkKoinModule() {
        val koin =
            startKoin {
                modules(coreModule(config), mongoStorageModule(config.mongo))
            }.koin

        assertNotNull(koin.get<TokenService>())
        assertNotNull(koin.get<AuthService>())
        assertNotNull(koin.get<UserRepository>())
        assertNotNull(koin.get<TokenRepository>())
        assertNotNull(koin.get<TransactionRepository>())
        assertNotNull(koin.get<CategoryRepository>())

        koin.get<MongoClient>().close()
    }
}
