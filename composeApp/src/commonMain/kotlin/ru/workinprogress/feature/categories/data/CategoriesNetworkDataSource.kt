package ru.workinprogress.feature.categories.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.patch
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import ru.workinprogress.feature.category.CategoryResource
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.DataSource

class CategoriesNetworkDataSource(private val httpClient: HttpClient) : DataSource<Category> {
    override suspend fun create(params: Category): Category = httpClient.post(CategoryResource()) {
        setBody(params)
    }.body()

    override suspend fun load(): List<Category> = httpClient.get(CategoryResource()).body()

    override suspend fun update(params: Category): Category? = httpClient.patch(CategoryResource.ById(id = params.id)) {
        setBody(params)
    }.takeIf { it.status == HttpStatusCode.Companion.OK }?.body()

    override suspend fun delete(id: String): Boolean =
        httpClient.delete(CategoryResource.ById(id = id)).status == HttpStatusCode.Companion.OK
}
