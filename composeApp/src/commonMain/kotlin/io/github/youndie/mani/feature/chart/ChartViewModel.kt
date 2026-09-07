package io.github.youndie.mani.feature.chart

import androidx.lifecycle.ViewModel
import io.github.youndie.mani.defaultMinDate
import io.github.youndie.mani.feature.chart.ui.model.ChartUi
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepository
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import io.github.youndie.mani.feature.transaction.toChartInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class ChartViewModel(currencyRepository: CurrentCurrencyRepository, transactionRepository: TransactionRepository) :
    ViewModel() {

    private val currency = currencyRepository.currency

    val observe = transactionRepository.dataStateFlow
        .filter(List<Transaction>::isNotEmpty)
        .map { toChart(transactions = it, from = defaultMinDate) }
        .flowOn(Dispatchers.Default)

    private fun toChart(transactions: List<Transaction>, from: LocalDate): ChartUi = ChartUi(
        transactions.toChartInternal()
            .let { chart -> chart.copy(days = chart.days.filter { it.key > from }) },
        currency,
    )
}
