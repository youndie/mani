package io.github.youndie.mani.feature.transaction.ui.model

import io.github.youndie.mani.emptyImmutableList
import io.github.youndie.mani.emptyImmutableMap
import io.github.youndie.mani.feature.main.ui.ServerUnreachableUiState
import io.github.youndie.mani.uiState.CommonUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.datetime.LocalDate

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
    /** Не `null` — сети нет, и показано последнее известное, снятое в это время. */
    val showingCacheFrom: String? = null,
    /** Не `null` — сервер не ответил и показать нечего: ни свежего, ни сохранённого. */
    val unreachable: ServerUnreachableUiState? = null,
) : CommonUiState<TransactionsByDays> {
    override fun load() = copy(loading = true)
    override fun showError(message: String) = copy(errorMessage = message, loading = false)
    override fun showData(data: TransactionsByDays) = copy(data = data, loading = false)
}
