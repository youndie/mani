package ru.workinprogress.feature.currency

import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing

fun Routing.currencyRouting() {
    get<CurrencyResource> {
        call.respond(HttpStatusCode.OK, listOf(Currency.Rub, Currency.Usd))
    }
}
