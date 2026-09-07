package io.github.youndie.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.github.youndie.mani.config.JWTConfig
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.auth.data.AuthService
import io.github.youndie.mani.feature.category.CategoryRepository
import io.github.youndie.mani.feature.transaction.data.TransactionRepository
import io.github.youndie.mani.feature.user.data.TokenRepository
import io.github.youndie.mani.feature.user.data.UserRepository
import io.github.youndie.mani.security.TokenService
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
