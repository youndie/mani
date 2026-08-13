package ru.workinprogress.feature.transaction

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import com.russhwolf.settings.Settings
import ru.workinprogress.feature.transaction.data.TransactionRepositoryImpl
import ru.workinprogress.feature.transaction.data.TransactionsCache
import ru.workinprogress.feature.transaction.data.TransactionsNetworkDataSource
import ru.workinprogress.feature.transaction.domain.*
import ru.workinprogress.feature.transaction.ui.TransactionsViewModel

val transactionsModule = module {
    single { Dispatchers.Default }.bind<CoroutineDispatcher>()
    singleOf(::DeleteTransactionsUseCase)
    singleOf(::GetTransactionsUseCase)
    singleOf(::GetTransactionUseCase)
    singleOf(::AddTransactionUseCase)
    singleOf(::UpdateTransactionUseCase)
    singleOf(::TransactionsNetworkDataSource)
    // Настройки без аргументов: multiplatform-settings сам подбирает хранилище платформы.
    // Отдельным определением, а не внутри кэша: проверка графа обходит конструкторы и иначе
    // не видит зависимость.
    single<Settings> { Settings() }
    single<TransactionsCache> { TransactionsCache(get()) }
    singleOf(::TransactionRepositoryImpl).bind<TransactionRepository>()
    viewModelOf(::TransactionsViewModel)
}

