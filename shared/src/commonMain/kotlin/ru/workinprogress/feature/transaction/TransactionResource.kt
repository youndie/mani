package ru.workinprogress.feature.transaction

import io.ktor.resources.*

@Resource("/transactions")
class TransactionResource {

    @Resource("/{id}")
    class ById(val parent: TransactionResource = TransactionResource(), val id: String)
}
