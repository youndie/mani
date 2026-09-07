package io.github.youndie.mani.feature.categories

import io.github.youndie.mani.feature.categories.data.CategoriesNetworkDataSource
import io.github.youndie.mani.feature.categories.data.CategoriesRepository
import io.github.youndie.mani.feature.categories.domain.AddCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.DeleteCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.GetCategoriesUseCase
import io.github.youndie.mani.feature.categories.domain.ObserveCategoriesUseCase
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.DataSource
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

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
