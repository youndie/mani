package io.github.youndie.mani.feature.auth

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.domain.AuthUseCase
import io.github.youndie.mani.feature.auth.domain.DemoUseCase
import io.github.youndie.mani.feature.auth.ui.AuthViewModel
import io.github.youndie.mani.feature.auth.ui.model.AuthUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.*

/**
 * Форма входа: что видит человек после нажатия.
 *
 * Модель общая у входа и регистрации, поэтому проверяются оба исхода каждого пути — успех
 * снимает форму, отказ оставляет её с текстом ошибки и снятой загрузкой. Последнее не
 * мелочь: форма с вечным спиннером выглядит как зависшее приложение, а не как отказ.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest : KoinTest {

    private lateinit var viewModel: AuthViewModel

    private var useCaseSuccess = true

    @BeforeTest
    fun setUp() {
        // Диспетчер подменяется ДО построения модели: её `init` уже уходит в `viewModelScope`,
        // то есть на Main, и построенная раньше подмены модель стартует на настоящем.
        Dispatchers.setMain(StandardTestDispatcher())

        startKoin {
            modules(testModule { useCaseSuccess })
        }
        viewModel = get()
    }

    @Test
    fun testSimple() = runTest {
        assertEquals(AuthUiState(), viewModel.observe.value)

        viewModel.onUsernameChanged("username")
        viewModel.onPasswordChanged("password")

        assertEquals(AuthUiState(username = "username", password = "password"), viewModel.observe.value)

        viewModel.onLoginClicked()

        runCurrent()

        assertNull(viewModel.observe.value.errorMessage)
        assertTrue(viewModel.observe.value.loading)

        runCurrent()

        assertNull(viewModel.observe.value.errorMessage)
        assertTrue(viewModel.observe.value.success)
    }

    @Test
    fun testFail() = runTest {
        useCaseSuccess = false

        assertEquals(AuthUiState(), viewModel.observe.value)

        viewModel.onUsernameChanged("username")
        viewModel.onPasswordChanged("password")

        assertEquals(AuthUiState(username = "username", password = "password"), viewModel.observe.value)

        viewModel.onLoginClicked()

        runCurrent()

        assertFalse(viewModel.observe.value.success)
        assertEquals(errorMessage, viewModel.observe.value.errorMessage)
    }

    @Test
    fun testDemoSuccess() = runTest {
        useCaseSuccess = true

        viewModel.onTryDemoClicked()
        advanceUntilIdle()

        assertTrue(viewModel.observe.value.success)
        assertNull(viewModel.observe.value.errorMessage)
    }

    @Test
    fun testDemoError() = runTest {
        useCaseSuccess = false

        viewModel.onTryDemoClicked()
        advanceUntilIdle()

        assertFalse(viewModel.observe.value.success)
        assertFalse(viewModel.observe.value.demoLoading)
        assertEquals(errorMessage, viewModel.observe.value.errorMessage)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }
}

private val errorMessage = "fake error"

class FakeAuthUseCase(private val success: () -> Boolean) : AuthUseCase() {
    override suspend fun invoke(params: LoginParams) = if (success()) {
        Result.success(true)
    } else {
        Result.failure(ServerException(errorMessage, null))
    }
}

class FakeDemoUseCase(private val success: () -> Boolean) : DemoUseCase() {
    override suspend fun invoke(params: io.github.youndie.mani.useCase.EmptyParams) = if (success()) {
        Result.success(true)
    } else {
        Result.failure(ServerException(errorMessage, null))
    }
}

private fun testModule(success: () -> Boolean) = module {
    single<AuthUseCase> {
        FakeAuthUseCase(success)
    }

    single<DemoUseCase> {
        FakeDemoUseCase(success)
    }

    single<AuthViewModel> { AuthViewModel(get(), get()) }
}
