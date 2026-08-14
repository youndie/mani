package ru.workinprogress.feature.transaction.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.patch
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import ru.workinprogress.feature.transaction.DataSource
import ru.workinprogress.feature.transaction.Transaction
import ru.workinprogress.feature.transaction.TransactionResource

class TransactionsNetworkDataSource(private val httpClient: HttpClient) : DataSource<Transaction> {

    override suspend fun create(params: Transaction): Transaction = httpClient.post(TransactionResource()) {
        setBody(params)
    }.body<Transaction>()

    override suspend fun load(): List<Transaction> = httpClient.get(TransactionResource()).body<List<Transaction>>()

    override suspend fun update(params: Transaction): Transaction? {
        val response = httpClient.patch(TransactionResource.ById(id = params.id)) {
            setBody(params)
        }
        return if (response.status == HttpStatusCode.Companion.OK) {
            response.body()
        } else {
            null
        }
    }

    override suspend fun delete(id: String): Boolean =
        httpClient.delete(TransactionResource.ById(id = id)).status == HttpStatusCode.Companion.OK
}
