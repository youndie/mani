package ru.workinprogress.feature.transaction

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface WithId {
    val id: String
}

interface StateFlowRepository<T : WithId> {
    val dataStateFlow: StateFlow<List<T>>
    suspend fun load()
    fun getById(id: String): T
    suspend fun create(params: T): T
    suspend fun update(params: T): Boolean
    suspend fun delete(id: String): Boolean
    fun reset()
}

interface DataSource<T : WithId> {
    suspend fun create(params: T): T
    suspend fun load(): List<T>
    suspend fun update(params: T): T?
    suspend fun delete(id: String): Boolean
}

abstract class BaseFlowRepository<T : WithId>(private val dataSource: DataSource<T>) : StateFlowRepository<T> {
    private val data = MutableStateFlow(emptyList<T>())
    private val dispatcher = Dispatchers.Default
    override val dataStateFlow: StateFlow<List<T>> = data.asStateFlow()

    override suspend fun create(params: T): T {
        data.value += params

        try {
            val created = dataSource.create(params)

            data.value = data.value - params + created
            return created
        } catch (e: Exception) {
            data.value -= params
            throw e
        }
    }

    override suspend fun load() = withContext(dispatcher) {
        data.value = dataSource.load()
    }

    /** Подставить список, минуя источник, — нужно наследнику, показывающему кэш без сети. */
    protected fun replaceAll(items: List<T>) {
        data.value = items
    }

    override fun getById(transactionId: String): T = dataStateFlow.value.first { it.id == transactionId }

    override suspend fun update(params: T): Boolean {
        val old = data.value.first { it.id == params.id }

        data.value -= old

        try {
            val updated = dataSource.update(params)

            if (updated != null) {
                data.value += updated
                return true
            } else {
                data.value += old
            }
        } catch (e: Exception) {
            data.value += old
            throw e
        }

        return false
    }

    override suspend fun delete(transactionId: String): Boolean {
        val transaction = dataStateFlow.value.find { it.id == transactionId } ?: return true

        data.value -= transaction

        try {
            return dataSource.delete(transactionId)
        } catch (e: Exception) {
            data.value += transaction
            throw e
        }

        return false
    }

    override fun reset() {
        this.data.value = emptyList()
    }
}
