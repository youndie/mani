package ru.workinprogress.mani

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.feature.auth.domain.AuthUseCase
import ru.workinprogress.feature.auth.ui.AuthViewModel
import ru.workinprogress.feature.main.MainViewModel
import ru.workinprogress.feature.welcome.WelcomeViewModel
import org.koin.test.verify.verify
import ru.workinprogress.mani.appModules
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.koin.core.qualifier.named
import ru.workinprogress.feature.categories.CATEGORIES_SOURCE
import ru.workinprogress.feature.transaction.TRANSACTIONS_SOURCE
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import ru.workinprogress.feature.categories.data.CategoriesNetworkDataSource
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.DataSource
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.data.TransactionsNetworkDataSource

/**
 * `verify()` обходит только модули приложения. Виртуальные машины экранов регистрируются
 * **внутри компонентов** через `rememberKoinModules`, поэтому их зависимости сюда не попадают:
 * недостающая привязка обнаруживается не тестом, а чёрным экраном в браузере. Так и случилось с
 * `SeedUseCase` — тесты были зелёные, а приложение падало при открытии главного экрана.
 *
 * Ниже к проверке добавлены типы, которые эти машины требуют от графа приложения.
 */
class ClientKoinModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun checkKoinModule() {
        module {
            includes(appModules)
            // Регистрируются в компонентах, но зависимости берут из графа приложения.
            viewModelOf(::MainViewModel)
            viewModelOf(::AuthViewModel)
            viewModelOf(::WelcomeViewModel)
        }.verify(
            extraTypes = listOf(
                HttpClientEngine::class,
                HttpClientConfig::class,
                // Привязку выбирает экран: вход даёт `LoginUseCase`, регистрация — `SignupUseCase`.
                AuthUseCase::class,
            ),
        )
    }
}
/**
 * Разные источники данных не должны путаться местами.
 *
 * `DataSource<T>` — обобщённый интерфейс, а обобщения стираются: для Koin `DataSource<Category>` и
 * `DataSource<Transaction>` — один и тот же ключ. Кто зарегистрировался под ним последним, того и
 * получают все. В браузере это выглядело как `ClassCastException: Cannot cast instance of
 * Category to Transaction` при открытии главного экрана.
 */
class DataSourceBindingTest : KoinTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun sourcesAreBoundByName() {
        val koin = startKoin { modules(appModules) }.koin

        assertIs<TransactionsNetworkDataSource>(
            koin.get<DataSource<Transaction>>(named(TRANSACTIONS_SOURCE)),
            "лента правил получила чужой источник данных",
        )
        assertIs<CategoriesNetworkDataSource>(
            koin.get<DataSource<Category>>(named(CATEGORIES_SOURCE)),
            "категории получили чужой источник данных",
        )

        // И ни один источник не висит на безымянном ключе: именно он и путал их местами, потому
        // что обобщение в нём стирается. Вернётся — тест покраснеет здесь, а не в браузере.
        assertNull(
            koin.getOrNull<DataSource<Transaction>>(),
            "источник данных снова привязан к обобщённому интерфейсу без имени",
        )
    }
}
