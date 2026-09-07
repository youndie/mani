package io.github.youndie.mani.feature.chart

import io.github.youndie.mani.utilz.bigdecimal.BigDecimalSerializable
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ChartResponse(val days: Map<LocalDate, BigDecimalSerializable>, val from: LocalDate, val to: LocalDate) {
    companion object {
        val Empty =
            ChartResponse(emptyMap(), LocalDate.fromEpochDays(0), LocalDate.fromEpochDays(0))
    }
}
