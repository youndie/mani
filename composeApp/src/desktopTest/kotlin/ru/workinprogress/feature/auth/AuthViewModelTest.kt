package ru.workinprogress.feature.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import ru.workinprogress.feature.auth.domain.AuthUseCase
import ru.workinprogress.feature.auth.domain.DemoUseCase
import ru.workinprogress.feature.auth.ui.AuthViewModel
import ru.workinprogress.feature.auth.ui.model.AuthUiState
import ru.workinprogress.mani.data.ServerException
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest : KoinTest {

    private lateinit var viewModel: AuthViewModel

    private var useCaseSuccess = true

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(testModule { useCaseSuccess })
        }
        viewModel = get()
        Dispatchers.setMain(StandardTestDispatcher())
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
        Result.Success(true)
    } else {
        Result.Error(ServerException(errorMessage, null))
    }
}

class FakeDemoUseCase(private val success: () -> Boolean) : DemoUseCase() {
    override suspend fun invoke(params: ru.workinprogress.useCase.EmptyParams) = if (success()) {
        Result.Success(true)
    } else {
        Result.Error(ServerException(errorMessage, null))
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
