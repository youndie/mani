package io.github.youndie.mani.feature.transaction.data

import io.github.youndie.mani.feature.transaction.BaseFlowRepository
import io.github.youndie.mani.feature.transaction.Transaction
import io.github.youndie.mani.feature.transaction.domain.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Список правил с последним известным состоянием на случай отсутствия сети.
 *
 * Порядок важен: сеть спрашивается всегда, кэш подставляется только если она отказала. Иначе
 * приложение показывало бы вчерашние данные и при живом соединении.
 */
@OptIn(ExperimentalTime::class)
class TransactionRepositoryImpl(
    source: io.github.youndie.mani.feature.transaction.DataSource<Transaction>,
    private val cache: TransactionsCache,
) : BaseFlowRepository<Transaction>(source),
    TransactionRepository {

    private val staleSince = MutableStateFlow<Instant?>(null)

    /** Не `null` — показанное взято из кэша, снятого в это время. */
    override val showingCacheFrom: StateFlow<Instant?> = staleSince.asStateFlow()

    override suspend fun load() {
        try {
            super.load()
            cache.save(dataStateFlow.value)
            staleSince.value = null
        } catch (e: CancellationException) {
            // Отмена — не отказ сети. Без этой ветки уход с экрана подставлял бы кэш и зажигал
            // «показаны последние известные данные» там, где соединение живо.
            throw e
        } catch (e: Exception) {
            val cached = cache.load() ?: throw e

            replaceAll(cached.transactions)
            staleSince.value = cached.takenAt
        }
    }
}
