package io.github.youndie.mani.feature.welcome

import io.github.youndie.mani.data.ServerException
import io.github.youndie.mani.feature.auth.domain.DemoUseCase
import io.github.youndie.mani.feature.health.Health
import io.github.youndie.mani.feature.health.domain.HealthUseCase
import io.github.youndie.mani.useCase.EmptyParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeHealthUseCase(private val answer: () -> Result<Health>) : HealthUseCase() {
    override suspend fun invoke(params: EmptyParams): Result<Health> = answer()
}

private class FakeDemoUseCase : DemoUseCase() {
    var called = false

    override suspend fun invoke(params: EmptyParams): Result<Boolean> {
        called = true
        return Result.success(true)
    }
}

/**
 * Витрина называет сборку, которая ответила.
 *
 * Ради этого проект и существует: посетитель видит, что его запрос обслужил нативный бинарь, а не
 * читает об этом в README. Значение обязано приходить живым ответом — константа в клиенте не
 * доказывала бы ничего.
 *
 * Вторая половина не менее важна: отказ `/health` молчаливый. Строка — украшение, и её пропажа не
 * должна ни показывать ошибку, ни мешать войти в демо.
 */
class WelcomeViewModelTest {

    /**
     * Значения нарочно непохожи на настоящие.
     *
     * С правдоподобными («kotlin/native», «1.4.2») тест проходил бы и над строкой, зашитой
     * константой, — то есть не проверял бы того, ради чего сценарий написан: строка приходит
     * ОТВЕТОМ. Проверено мутацией: с прежней фикстурой зашитая строка проходила.
     */
    private val health = Health(build = "answered-by-fixture", version = "0.0.0-test", uptimeSeconds = 12)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theAnsweringBuildIsNamedOnTheScreen() = runTest {
        val viewModel = WelcomeViewModel(FakeDemoUseCase(), FakeHealthUseCase { Result.success(health) })
        runCurrent()

        assertEquals("ktor · answered-by-fixture · 0.0.0-test", viewModel.observe.value.server)
    }

    /** Отказ здесь молчит: ни строки, ни сообщения об ошибке. */
    @Test
    fun aFailingHealthIsSilent() = runTest {
        val viewModel = WelcomeViewModel(
            FakeDemoUseCase(),
            FakeHealthUseCase { Result.failure(ServerException("Couldn't reach the server")) },
        )
        runCurrent()

        assertNull(viewModel.observe.value.server, "строка о сборке появилась при отказе")
        assertNull(viewModel.observe.value.errorMessage, "отказ витрины показан как ошибка")
    }

    /** И не мешает войти: демо открывается, даже когда о сборке сказать нечего. */
    @Test
    fun aFailingHealthDoesNotBlockTheDemo() = runTest {
        val demo = FakeDemoUseCase()
        val viewModel = WelcomeViewModel(demo, FakeHealthUseCase { Result.failure(ServerException()) })
        runCurrent()

        viewModel.onTryDemoClicked()
        runCurrent()

        assertTrue(demo.called, "вход в демо не состоялся")
        assertTrue(viewModel.observe.value.success)
    }
}
