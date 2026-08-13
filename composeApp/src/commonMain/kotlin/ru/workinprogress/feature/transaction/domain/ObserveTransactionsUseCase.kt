package ru.workinprogress.feature.transaction.domain

/**
 * Последний известный список правил — без обращения к сети.
 *
 * Форме он нужен не для показа, а чтобы посчитать, как новое правило сдвинет день обнуления:
 * сдвиг измеряется относительно всех остальных правил. Грузить ради этого заново нечего —
 * экран, с которого сюда пришли, список уже загрузил.
 */
class ObserveTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    val observe = transactionRepository.dataStateFlow
}
