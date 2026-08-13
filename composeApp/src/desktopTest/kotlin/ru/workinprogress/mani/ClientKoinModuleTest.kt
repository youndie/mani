package ru.workinprogress.mani

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.workinprogress.feature.auth.domain.AuthUseCase
import ru.workinprogress.feature.auth.ui.AuthViewModel
import ru.workinprogress.feature.main.MainViewModel
import org.koin.test.verify.verify
import ru.workinprogress.mani.appModules
import kotlin.test.Test

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