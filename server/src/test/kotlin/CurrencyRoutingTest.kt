package io.github.youndie.mani

import io.github.youndie.mani.feature.currency.Currency
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Список валют — единственный маршрут, который никто не вызывает.
 *
 * Выбора валюты в продукте нет: клиент форматирует суммы валютой из своих настроек, а сервер
 * отдаёт зашитую пару. Пока маршрут существует, он обязан отвечать — и отвечать без токена,
 * потому что список валют не чьи-то данные.
 */
class CurrencyRoutingTest {

    @Test
    fun `the currency list is public and holds what the product supports`() = maniTest {
        val response = createClient { }.get("/currencies")

        assertEquals(HttpStatusCode.OK, response.status)

        val currencies = maniJson.decodeFromString<List<Currency>>(response.bodyAsText())
        assertEquals(listOf(Currency.Rub, Currency.Usd), currencies)
    }
}
