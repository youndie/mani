package io.github.youndie.mani.feature.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.youndie.mani.emptyImmutableMap
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.main.MainViewModel
import io.github.youndie.mani.feature.main.MainViewModel.Companion.loadingItems
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.amountSigned
import io.github.youndie.mani.feature.transaction.domain.DeleteTransactionsUseCase
import io.github.youndie.mani.feature.transaction.domain.GetTransactionsUseCase
import io.github.youndie.mani.feature.transaction.simulate
import io.github.youndie.mani.feature.transaction.ui.model.TransactionListUiState
import io.github.youndie.mani.feature.transaction.ui.model.TransactionUiItem
import io.github.youndie.mani.feature.transaction.ui.model.buildColoredAmount
import io.github.youndie.mani.feature.transaction.ui.model.formatMoney
import io.github.youndie.mani.today
import io.github.youndie.mani.utilz.bigdecimal.sumOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

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
            getTransactionsUseCase().fold(
                onSuccess = { transactionsFlow ->
                    state.update { state -> state.copy(loading = false, data = emptyImmutableMap()) }

                    transactionsFlow.mapLatest { transactions ->
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
                },
                onFailure = { throwable ->
                    state.value = TransactionListUiState(errorMessage = throwable.message.orEmpty())
                },
            )
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
