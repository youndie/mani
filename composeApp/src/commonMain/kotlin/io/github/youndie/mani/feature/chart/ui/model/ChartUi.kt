package io.github.youndie.mani.feature.chart.ui.model

import io.github.youndie.mani.emptyImmutableMap
import io.github.youndie.mani.feature.chart.ChartResponse
import io.github.youndie.mani.feature.currency.Currency
import io.github.youndie.mani.today
import io.github.youndie.mani.utilz.bigdecimal.BigDecimalSerializable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ChartUi(
    val days: ImmutableMap<LocalDate, BigDecimalSerializable> = persistentMapOf(),
    val from: LocalDate = LocalDate(2000, 1, 1),
    val to: LocalDate = LocalDate(2001, 4, 1),
    val currency: Currency = Currency.Usd,
    val loading: Boolean = false,
    val todayIndexProvider: () -> Int = { days.entries.indexOfFirst { entry -> entry.key == today() } },
) {
    companion object {
        val Loading = ChartUi(emptyImmutableMap(), today(), today(), Currency.Usd, true)

        operator fun invoke(chart: ChartResponse, currency: Currency): ChartUi =
            ChartUi(chart.days.toImmutableMap(), chart.from, chart.to, currency)
    }
}
