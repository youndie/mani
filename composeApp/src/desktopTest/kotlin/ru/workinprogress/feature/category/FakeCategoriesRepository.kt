package ru.workinprogress.feature.category

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.DataSource
import ru.workinprogress.feature.transaction.WithId

open class StateFlowDataSource<T : WithId> : DataSource<T> {

    var withError = false

    val stateFlow = MutableStateFlow(listOf<T>())

    override suspend fun create(params: T): T {
        if (withError) throw RuntimeException("error")
        stateFlow.update { items ->
            items + params
        }
        return params
    }

    override suspend fun load(): List<T> = stateFlow.value

    override suspend fun update(params: T): T? {
        stateFlow.update { items ->
            items - items.first { it.id == params.id } + params
        }
        return params
    }

    override suspend fun delete(id: String): Boolean {
        if (withError) throw RuntimeException("error")

        stateFlow.update { items ->
            items - items.first { it.id == id }
        }
        return true
    }
}

class FakeCategoriesDataSource : StateFlowDataSource<Category>()
