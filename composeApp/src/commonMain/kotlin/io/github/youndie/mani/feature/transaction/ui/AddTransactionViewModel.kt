package io.github.youndie.mani.feature.transaction.ui

import androidx.lifecycle.viewModelScope
import io.github.youndie.mani.feature.categories.domain.AddCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.DeleteCategoryUseCase
import io.github.youndie.mani.feature.categories.domain.ObserveCategoriesUseCase
import io.github.youndie.mani.feature.currency.GetCurrentCurrencyUseCase
import io.github.youndie.mani.feature.transaction.domain.AddTransactionUseCase
import io.github.youndie.mani.feature.transaction.domain.ObserveTransactionsUseCase
import io.github.youndie.mani.feature.transaction.ui.model.TransactionUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase,
    addCategoryUseCase: AddCategoryUseCase,
    observeCategoriesUseCase: ObserveCategoriesUseCase,
    deleteCategoryUseCase: DeleteCategoryUseCase,
    getCurrentCurrencyUseCase: GetCurrentCurrencyUseCase,
    observeTransactionsUseCase: ObserveTransactionsUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseTransactionViewModel(
    addCategoryUseCase,
    observeCategoriesUseCase,
    deleteCategoryUseCase,
    observeTransactionsUseCase,
    dispatcher,
) {

    init {
        viewModelScope.launch(dispatcher) {
            state.update {
                it.copy(
                    currency = getCurrentCurrencyUseCase.get(),
                )
            }
        }

        observeCategories()
        observeTransactions()
    }

    override fun onSubmitClicked() {
        viewModelScope.launch {
            state.update {
                it.copy(loading = true)
            }

            val result = withContext(dispatcher) { addTransactionUseCase(state.value.tempTransaction) }
            result.fold(
                onSuccess = {
                    state.update { TransactionUiState(success = true) }
                },
                onFailure = { throwable ->
                    state.update {
                        it.copy(loading = false, errorMessage = throwable.message)
                    }
                },
            )
        }
    }
}
