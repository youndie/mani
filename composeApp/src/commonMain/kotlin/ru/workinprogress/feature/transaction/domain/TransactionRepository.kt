package ru.workinprogress.feature.transaction.domain

import kotlinx.coroutines.flow.StateFlow
import ru.workinprogress.feature.transaction.StateFlowRepository
import ru.workinprogress.feature.transaction.Transaction
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
interface TransactionRepository : StateFlowRepository<Transaction> {
    override val dataStateFlow: StateFlow<List<Transaction>>
    override suspend fun load()
    override fun getById(transactionId: String): Transaction
    override suspend fun create(params: Transaction): Transaction
    override suspend fun update(params: Transaction): Boolean
    override suspend fun delete(transactionId: String): Boolean
    override fun reset()

    /**
     * Не `null` — сети не было, и показано последнее известное, снятое в это время.
     *
     * В интерфейсе, а не в реализации: экрану нужно об этом сказать, и знать про конкретный
     * класс репозитория он для этого не должен.
     */
    val showingCacheFrom: StateFlow<Instant?>
}
