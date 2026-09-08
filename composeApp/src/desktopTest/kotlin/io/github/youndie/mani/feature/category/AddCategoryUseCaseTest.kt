package io.github.youndie.mani.feature.category

import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.categories.domain.AddCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.ObserveCategoriesUseCase
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.DataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Заведение категории и то, что видит подписанный на список экран.
 *
 * Категорию заводят прямо из формы траты, не уходя с неё, поэтому список обязан обновиться сам —
 * без перечитывания. И обязан вернуться к прежнему виду, если сервер запись не принял: иначе в
 * выпадающем списке остаётся имя, которого на сервере нет, и следующая трата ссылается в пустоту.
 */
class AddCategoryUseCaseTest {

    /** Источник, отвечающий тем же именем, но своим идентификатором, — так делает сервер. */
    private class ServerAssigningIds : DataSource<Category> {
        val stored = mutableListOf<Category>()

        override suspend fun create(params: Category): Category =
            Category("server-${stored.size + 1}", params.name).also { stored += it }

        override suspend fun load(): List<Category> = stored
        override suspend fun update(params: Category): Category? = params
        override suspend fun delete(id: String): Boolean = true
    }

    @Test
    fun aNewCategoryReachesTheListWithoutAReload() = runTest {
        val repository = CategoriesRepository(FakeCategoriesDataSource())
        val observed = ObserveCategoriesUseCase(repository).observe

        val result = AddCategoryUseCase(repository)(Category("local", "Food"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("Food"), observed.value.map { it.name })
    }

    /**
     * Имя категории живёт под тем идентификатором, который назвал сервер.
     *
     * Локальный подставляется только до ответа. Останься он в списке — выбранная в форме
     * категория уехала бы на сервер с идентификатором, которого там нет.
     */
    @Test
    fun theServersIdentifierReplacesTheLocalOne() = runTest {
        val repository = CategoriesRepository(ServerAssigningIds())
        val observed = ObserveCategoriesUseCase(repository).observe

        val result = AddCategoryUseCase(repository)(Category("local", "Food"))

        assertEquals("server-1", result.getOrNull()?.id)
        assertEquals(listOf("server-1"), observed.value.map { it.id })
    }

    /** Отказ сервера — отказ наружу, и список возвращается к прежнему виду. */
    @Test
    fun aRefusedCategoryLeavesNoTraceInTheList() = runTest {
        val dataSource = FakeCategoriesDataSource().apply { withError = true }
        val repository = CategoriesRepository(dataSource)
        val observed = ObserveCategoriesUseCase(repository).observe

        val result = AddCategoryUseCase(repository)(Category("local", "Food"))

        assertTrue(result.isFailure)
        assertTrue(observed.value.isEmpty(), "непринятая категория осталась в списке")
    }

    /** Подписка одна на всех: список, взятый до записи, обновляется вместе с хранилищем. */
    @Test
    fun theListTakenBeforeTheWriteIsTheSameList() = runTest {
        val repository = CategoriesRepository(FakeCategoriesDataSource())
        val takenEarly = ObserveCategoriesUseCase(repository).observe
        val takenLate = ObserveCategoriesUseCase(repository).observe

        AddCategoryUseCase(repository)(Category("local", "Food"))

        assertEquals(takenLate.value, takenEarly.value)
        assertEquals(1, takenEarly.value.size)
    }
}
