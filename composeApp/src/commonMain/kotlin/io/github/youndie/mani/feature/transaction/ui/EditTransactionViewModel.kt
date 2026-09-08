package io.github.youndie.mani.feature.transaction.ui

import androidx.lifecycle.viewModelScope
import io.github.youndie.mani.feature.categories.domain.AddCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.DeleteCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.ObserveCategoriesUseCase
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.GetTransactionUseCase
import io.github.youndie.mani.feature.transaction.domain.ObserveTransactionsUseCase
import io.github.youndie.mani.feature.transaction.domain.UpdateTransactionUseCase
import io.github.youndie.mani.feature.transaction.ui.model.TransactionUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditTransactionViewModel(
    private val transactionId: String,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    addCategoryUseCase: AddCategoryUseCase,
    observeCategoriesUseCase: ObserveCategoriesUseCase,
    getCurrentCurrencyUseCase: GetCurrentCurrencyUseCase,
    deleteCategoryUseCase: DeleteCategoryUseCase,
    observeTransactionsUseCase: ObserveTransactionsUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseTransactionViewModel(
    addCategoryUseCase,
    observeCategoriesUseCase,
    deleteCategoryUseCase,
    observeTransactionsUseCase,
    dispatcher,
) {

    override val state: MutableStateFlow<TransactionUiState> = MutableStateFlow(TransactionUiState(edit = true))

    init {
        viewModelScope.launch(dispatcher) {
            // Правило может не найтись — ссылку открыли по устаревшему идентификатору. Это
            // сообщение на форме, а не падение: `get()` бросил бы отказ прямо в корутину.
            getTransactionUseCase(transactionId).fold(
                onSuccess = { transaction ->
                    state.value = TransactionUiState(
                        transaction,
                        currency = getCurrentCurrencyUseCase.get(),
                    ).copy(
                        edit = true,
                        periods = Transaction.Period.entries.toImmutableList(),
                    )

                    observeCategories()
                    observeTransactions()
                },
                onFailure = { throwable ->
                    state.update { it.copy(loading = false, errorMessage = throwable.message) }
                },
            )
        }
    }

    override fun onSubmitClicked() {
        viewModelScope.launch {
            state.update { it.copy(loading = true) }

            val result = updateTransactionUseCase(state.value.tempTransaction)

            result.fold(
                onSuccess = {
                    state.update { TransactionUiState(success = true) }
                },
                onFailure = { throwable ->
                    state.update {
                        it.copy(errorMessage = throwable.message, loading = false)
                    }
                },
            )
        }
    }
}
