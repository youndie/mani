package io.github.youndie.mani.feature.currency

import com.russhwolf.settings.MapSettings
import io.github.youndie.mani.feature.currency.data.CurrentCurrencyRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Выбранная валюта переживает перезапуск.
 *
 * От неё зависит форматирование сумм на каждом экране, а хранится она одна на приложение. Если
 * выбор не сохраняется, человек видит доллары там, где выбрал рубли, — и ничто не падает.
 */
class CurrentCurrencyRepositoryTest {

    @Test
    fun theDefaultIsTheDollar() {
        assertEquals(Currency.Usd, CurrentCurrencyRepositoryImpl(MapSettings()).currency)
    }

    /** Новый экземпляр поверх того же хранилища — это и есть следующий запуск приложения. */
    @Test
    fun theChoiceSurvivesARestart() {
        val settings = MapSettings()
        CurrentCurrencyRepositoryImpl(settings).currency = Currency.Rub

        assertEquals(Currency.Rub, CurrentCurrencyRepositoryImpl(settings).currency)
    }

    /** Хранится валюта целиком, а не один её код: символ рисуется рядом с каждой суммой. */
    @Test
    fun theStoredCurrencyKeepsItsSymbol() {
        val settings = MapSettings()
        CurrentCurrencyRepositoryImpl(settings).currency = Currency.Rub

        val restored = CurrentCurrencyRepositoryImpl(settings).currency
        assertEquals(Currency.Rub.symbol, restored.symbol)
        assertEquals(Currency.Rub.name, restored.name)
    }
}
