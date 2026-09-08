package io.github.youndie.mani.feature.category

import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.categories.domain.DeleteCategoryUseCase
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.data.FakeTransactionsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DeleteCategoryUseCaseTest {

    /**
     * Отказ сети на перечитывании возвращается как [Result], а не роняет приложение.
     *
     * Перечитывание стояло в `also`, снаружи `suspendRunCatching`: его исключение проходило мимо
     * `Result` и всплывало в `viewModelScope.launch`, у которого обработчика нет. Снаружи это
     * выглядело как «удалил категорию в метро — приложение закрылось».
     */
    @Test
    fun aRefusedReloadComesBackAsFailure() = runTest {
        val useCase = DeleteCategoryUseCase(
            CategoriesRepository(FakeCategoriesDataSource()),
            FakeTransactionsRepository(shouldCrash = { true }),
        )

        val result = useCase(Category("1", "Food"))

        assertTrue(result.isFailure)
    }

    /** И то, ради чего перечитывание вообще есть: правила снятой категории должны обновиться. */
    @Test
    fun deletingACategoryReloadsTheRules() = runTest {
        val transactions = FakeTransactionsRepository()
        val useCase = DeleteCategoryUseCase(CategoriesRepository(FakeCategoriesDataSource()), transactions)

        val result = useCase(Category("1", "Food"))

        assertTrue(result.isSuccess)
        assertTrue(transactions.dataStateFlow.value.isNotEmpty(), "правила не перечитаны")
    }
}
