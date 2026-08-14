package ru.workinprogress.feature.transaction

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.transaction.data.TransactionRepositoryImpl
import ru.workinprogress.feature.transaction.data.TransactionsCache
import ru.workinprogress.feature.transaction.data.TransactionsNetworkDataSource
import ru.workinprogress.feature.transaction.domain.*
import ru.workinprogress.feature.transaction.ui.TransactionsViewModel

val transactionsModule = module {
    single { Dispatchers.Default }.bind<CoroutineDispatcher>()
    singleOf(::DeleteTransactionsUseCase)
    singleOf(::GetTransactionsUseCase)
    singleOf(::ObserveTransactionsUseCase)
    singleOf(::GetTransactionUseCase)
    singleOf(::AddTransactionUseCase)
    singleOf(::UpdateTransactionUseCase)
    single<DataSource<Transaction>>(named(TRANSACTIONS_SOURCE)) { TransactionsNetworkDataSource(get()) }
    // Настройки без аргументов: multiplatform-settings сам подбирает хранилище платформы.
    // Отдельным определением, а не внутри кэша: проверка графа обходит конструкторы и иначе
    // не видит зависимость.
    single<Settings> { Settings() }
    single<TransactionsCache> { TransactionsCache(get()) }
    // Источник берётся по имени: см. комментарий в `categoriesModule`.
    single<TransactionRepository> { TransactionRepositoryImpl(get(named(TRANSACTIONS_SOURCE)), get()) }
    viewModelOf(::TransactionsViewModel)
}

/** Имя привязки источника правил: обобщённый тип его не различает. */
const val TRANSACTIONS_SOURCE = "transactions-source"
