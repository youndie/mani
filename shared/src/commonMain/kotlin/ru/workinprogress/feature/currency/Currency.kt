package ru.workinprogress.feature.currency

import kotlinx.serialization.Serializable

@Serializable
data class Currency(val code: String, val name: String, val symbol: String) {
    companion object {
        val Rub = Currency("RUB", "Рубль", "Р")
        val Usd = Currency("USD", "United States Dollar", "$")
    }
}
