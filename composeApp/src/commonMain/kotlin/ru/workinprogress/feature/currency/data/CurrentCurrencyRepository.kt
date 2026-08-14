package ru.workinprogress.feature.currency.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import ru.workinprogress.feature.currency.Currency

interface CurrentCurrencyRepository {
    var currency: Currency
}

class CurrentCurrencyRepositoryImpl : CurrentCurrencyRepository {

    private val settings: Settings = Settings()

    @OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
    override var currency: Currency
        get() = settings.decodeValue(
            Currency.serializer(),
            Currency::class.simpleName.toString(),
            Currency.Usd,
        )
        set(value) {
            settings.encodeValue(
                Currency.serializer(),
                Currency::class.simpleName.toString(),
                value,
            )
        }
}
