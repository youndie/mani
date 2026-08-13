package ru.workinprogress.feature.main.ui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import ru.workinprogress.feature.transaction.ui.model.TransactionUiItem

data class MainUiState(
    val transactions: ImmutableMap<LocalDate, ImmutableList<TransactionUiItem>> =
        emptyMap<LocalDate, ImmutableList<TransactionUiItem>>().toImmutableMap(),
    val errorMessage: String? = null,
    val selectedTransactions: ImmutableList<TransactionUiItem> = emptyList<TransactionUiItem>().toImmutableList(),
    val showDeleteDialog: Boolean = false,
    val showProfile: Boolean = false,
    val forecast: ForecastUiState = ForecastUiState.Loading,
    val loading: Boolean = false,
    val filtersState: FiltersState = FiltersState(),
)

