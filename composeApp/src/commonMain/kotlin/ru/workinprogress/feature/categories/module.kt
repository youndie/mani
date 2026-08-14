package ru.workinprogress.feature.categories

import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.workinprogress.feature.categories.data.CategoriesNetworkDataSource
import ru.workinprogress.feature.categories.data.CategoriesRepository
import ru.workinprogress.feature.categories.domain.AddCategoryUseCase
import ru.workinprogress.feature.categories.domain.DeleteCategoryUseCase
import ru.workinprogress.feature.categories.domain.GetCategoriesUseCase
import ru.workinprogress.feature.categories.domain.ObserveCategoriesUseCase
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.DataSource

val categoriesModule = module {
    singleOf(::AddCategoryUseCase)
    singleOf(::DeleteCategoryUseCase)
    singleOf(::GetCategoriesUseCase)
    // Источник данных — под именем, а не просто под `DataSource<Category>`: обобщения
    // стираются, и для Koin `DataSource<Category>` и `DataSource<Transaction>` — один и тот же
    // ключ. Пока имён не было, репозиторий правил получал источник категорий, и главный экран
    // падал с `ClassCastException`. Имя оставляет подмену в тестах возможной, а путаницу — нет.
    single<DataSource<Category>>(named(CATEGORIES_SOURCE)) { CategoriesNetworkDataSource(get()) }
    single { CategoriesRepository(get(named(CATEGORIES_SOURCE))) }
    singleOf(::ObserveCategoriesUseCase)
}

/** Имя привязки источника категорий: обобщённый тип его не различает. */
const val CATEGORIES_SOURCE = "categories-source"
