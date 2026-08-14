package ru.workinprogress.feature.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import ru.workinprogress.feature.currency.Currency
import ru.workinprogress.feature.currency.GetCurrentCurrencyUseCase
import ru.workinprogress.feature.main.MainViewModel
import ru.workinprogress.feature.main.MainViewModel.Companion.loadingItems
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.amountSigned
import ru.workinprogress.feature.transaction.domain.DeleteTransactionsUseCase
import ru.workinprogress.feature.transaction.domain.GetTransactionsUseCase
import ru.workinprogress.feature.transaction.simulate
import ru.workinprogress.feature.transaction.ui.model.TransactionListUiState
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem
import ru.workinprogress.feature.transaction.ui.model.buildColoredAmount
import ru.workinprogress.feature.transaction.ui.model.formatMoney
import ru.workinprogress.mani.emptyImmutableMap
import ru.workinprogress.mani.today
import ru.workinprogress.useCase.UseCase
import ru.workinprogress.utilz.bigdecimal.sumOf

class TransactionsViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getCurrentCurrencyUseCase: GetCurrentCurrencyUseCase,
    private val deleteTransactionsUseCase: DeleteTransactionsUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(TransactionListUiState(loading = true, data = loadingItems))
    val observe = state.asStateFlow()

    init {
        load()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        viewModelScope.launch {
            state.value = TransactionListUiState(loading = true, data = loadingItems)

            val currency = getCurrentCurrencyUseCase.get()
            when (val result = getTransactionsUseCase()) {
                is UseCase.Result.Error -> {
                    state.value = TransactionListUiState(errorMessage = result.throwable.message.orEmpty())
                }

                is UseCase.Result.Success -> {
                    state.update { state -> state.copy(loading = false, data = emptyImmutableMap()) }

                    result.data.mapLatest { transactions ->
                        val simulated = transactions.simulate()

                        simulated
                            .filterValues { transactions -> transactions.isNotEmpty() }
                            .filterKeys {
                                today() > it
                            }
                            .mapValues { entry ->
                                entry.value.map { transaction ->
                                    TransactionUiItem(transaction, currency)
                                }.toImmutableList()
                            }
                            .entries
                            .sortedByDescending { it.key }
                            .associate {
                                it.key to it.value
                            }.toImmutableMap() to simulated
                    }.flowOn(Dispatchers.Default).collectLatest { (byDays, simulated) ->
                        state.value =
                            TransactionListUiState(
                                data = byDays,
                                dayBalances = MainViewModel.buildDayBalances(simulated, currency),
                                monthTitle = today().format(monthFormat) + " so far",
                                monthChange =
                                buildColoredAmount(
                                    simulated
                                        .filterKeys { it.year == today().year && it.month == today().month }
                                        .values
                                        .flatten()
                                        .sumOf { it.amountSigned },
                                    currency,
                                ).text,
                                balanceToday =
                                formatMoney(
                                    simulated
                                        .filterKeys { it <= today() }
                                        .values
                                        .flatten()
                                        .sumOf { it.amountSigned },
                                    currency,
                                ),
                            )
                    }
                }
            }
        }
    }

    fun onTransactionSelected(transactionUiItem: TransactionUiItem) {
        if (transactionUiItem in state.value.selectedTransactions) {
            state.update { state ->
                state.copy(
                    selectedTransactions = (state.selectedTransactions - transactionUiItem).toImmutableList(),
                )
            }
        } else {
            state.update { state ->
                state.copy(
                    selectedTransactions = (state.selectedTransactions + transactionUiItem).toImmutableList(),
                )
            }
        }
    }

    fun onShowDeleteDialogClicked() {
        state.update {
            it.copy(showDeleteDialog = true)
        }
    }

    fun onContextMenuClosed() {
        state.update { state ->
            state.copy(
                selectedTransactions = emptyList<TransactionUiItem>().toImmutableList(),
            )
        }
    }

    fun onDismissDeleteDialog() {
        state.update {
            it.copy(showDeleteDialog = false)
        }
    }

    fun onDeleteClicked() {
        viewModelScope.launch {
            val selected = state.value.selectedTransactions.map { it.id }
            state.update { state ->
                state.copy(
                    showDeleteDialog = false,
                    selectedTransactions = emptyList<TransactionUiItem>().toImmutableList(),
                )
            }

            deleteTransactionsUseCase(selected)
        }
    }
}

private val monthFormat = kotlinx.datetime.LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL)
}
