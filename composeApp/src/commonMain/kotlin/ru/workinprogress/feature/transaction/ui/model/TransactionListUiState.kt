package ru.workinprogress.feature.transaction.ui.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.datetime.LocalDate
import ru.workinprogress.mani.emptyImmutableList
import ru.workinprogress.mani.emptyImmutableMap
import ru.workinprogress.uiState.CommonUiState

data class TransactionListUiState(
    override val data: TransactionsByDays = emptyImmutableMap(),
    override val loading: Boolean = false,
    override val errorMessage: String? = null,
    val selectedTransactions: ImmutableList<TransactionUiItem> = emptyImmutableList(),
    val showDeleteDialog: Boolean = false,
    /** Баланс на конец каждого дня — тот же, что в ленте главного экрана. */
    val dayBalances: ImmutableMap<LocalDate, String> = emptyImmutableMap(),
    /** «August so far» — сколько накопилось за текущий месяц и каков баланс сегодня. */
    val monthTitle: String = "",
    val monthChange: String = "",
    val balanceToday: String = "",
) : CommonUiState<TransactionsByDays> {
    override fun load() = copy(loading = true)
    override fun showError(message: String) = copy(errorMessage = message, loading = false)
    override fun showData(data: TransactionsByDays) = copy(data = data, loading = false)
}
