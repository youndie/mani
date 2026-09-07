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
import io.github.youndie.mani.useCase.UseCase
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
            val transaction = getTransactionUseCase.get(transactionId)

            state.value = TransactionUiState(
                transaction,
                currency = getCurrentCurrencyUseCase.get(),
            ).copy(
                edit = true,
                periods = Transaction.Period.entries.toImmutableList(),
            )

            observeCategories()
            observeTransactions()
        }
    }

    override fun onSubmitClicked() {
        viewModelScope.launch {
            state.update { it.copy(loading = true) }

            val result = updateTransactionUseCase(state.value.tempTransaction)

            when (result) {
                is UseCase.Result.Success -> {
                    state.update { TransactionUiState(success = true) }
                }

                is UseCase.Result.Error -> {
                    state.update {
                        it.copy(errorMessage = result.throwable.message, loading = false)
                    }
                }
            }
        }
    }
}
