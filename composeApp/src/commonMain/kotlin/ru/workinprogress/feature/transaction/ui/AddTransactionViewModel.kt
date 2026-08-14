package ru.workinprogress.feature.transaction.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.workinprogress.feature.categories.domain.AddCategoryUseCase
import ru.workinprogress.feature.categories.domain.DeleteCategoryUseCase
import ru.workinprogress.feature.categories.domain.ObserveCategoriesUseCase
import ru.workinprogress.feature.currency.GetCurrentCurrencyUseCase
import ru.workinprogress.feature.transaction.domain.AddTransactionUseCase
import ru.workinprogress.feature.transaction.domain.ObserveTransactionsUseCase
import ru.workinprogress.feature.transaction.ui.model.TransactionUiState
import ru.workinprogress.useCase.UseCase

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
            when (result) {
                is UseCase.Result.Error -> {
                    state.update {
                        it.copy(loading = false, errorMessage = result.throwable.message)
                    }
                }

                is UseCase.Result.Success -> {
                    state.update { TransactionUiState(success = true) }
                }
            }
        }
    }
}

