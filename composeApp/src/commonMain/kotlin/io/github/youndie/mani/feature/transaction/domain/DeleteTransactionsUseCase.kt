package io.github.youndie.mani.feature.transaction.domain

import io.github.youndie.mani.useCase.UseCase
import io.github.youndie.mani.utilz.suspendRunCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DeleteTransactionsUseCase(
    private val repository: TransactionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(4),
) : UseCase<List<String>, Boolean>() {

    /**
     * Удаляет выбранное параллельно, но в рамках вызвавшей корутины: [coroutineScope] вместо
     * своего [kotlinx.coroutines.CoroutineScope] — иначе отмена экрана до задач не доходит,
     * а сам scope остаётся жить после возврата.
     *
     * Отказ одного удаления не отменяет остальные: человек выбрал список, и он должен быть
     * удалён настолько, насколько это вообще удалось. Поэтому каждое удаление ловит свой
     * отказ само, а наружу он всплывает уже после того, как отработали все.
     */
    override suspend fun invoke(params: List<String>): Result<Boolean> = suspendRunCatching {
        coroutineScope {
            params.map { transactionId ->
                async(dispatcher) { suspendRunCatching { repository.delete(transactionId) } }
            }.awaitAll()
        }.forEach { it.getOrThrow() }

        true
    }
}
