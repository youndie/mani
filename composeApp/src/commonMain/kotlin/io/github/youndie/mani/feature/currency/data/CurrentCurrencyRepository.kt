package io.github.youndie.mani.feature.currency.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import io.github.youndie.mani.feature.currency.Currency
import kotlinx.serialization.ExperimentalSerializationApi

interface CurrentCurrencyRepository {
    var currency: Currency
}

/**
 * Выбранная валюта живёт в настройках платформы.
 *
 * Хранилище приходит параметром: с `Settings()` внутри класса тест писал бы в настоящие настройки
 * машины, на которой идёт прогон, — прогоны мешали бы друг другу и оставляли бы за собой чужой
 * выбор. Умолчание оставлено, чтобы боевой вызов остался без аргументов.
 */
class CurrentCurrencyRepositoryImpl(private val settings: Settings = Settings()) : CurrentCurrencyRepository {

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
