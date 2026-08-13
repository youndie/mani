package ru.workinprogress.feature.demo.data

import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.data.UserRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Префикс имени, по которому песочница отличается от настоящего пользователя. */
const val DEMO_USERNAME_PREFIX = "demo-"

/** Сутки. Дольше песочницу никто не смотрит, а ссылку на витрину открывают заново. */
const val SANDBOX_LIFETIME_SECONDS = 24 * 60 * 60L

private const val OBJECT_ID_LENGTH = 24
private const val TIMESTAMP_HEX_LENGTH = 8
private const val HEX = 16

/**
 * Время создания документа, зашитое в сам идентификатор: первые четыре байта ObjectId — это
 * секунды Unix.
 *
 * Отсюда и берётся возраст песочницы: **отдельного поля с датой не заводится**. Иначе его
 * пришлось бы добавить в модель документа обеих сборок и мигрировать уже лежащие записи —
 * ради данных, которые и так есть в ключе.
 *
 * `null` — идентификатор не похож на ObjectId. Такую запись уборка не трогает: чужой формат
 * ключа означает, что документ завёл не этот код.
 */
internal fun String.objectIdCreatedAtSeconds(): Long? =
    takeIf { id -> id.length == OBJECT_ID_LENGTH && id.all { it in '0'..'9' || it in 'a'..'f' } }
        ?.take(TIMESTAMP_HEX_LENGTH)
        ?.toLongOrNull(HEX)

/**
 * Уносит песочницы, которые никто больше не откроет.
 *
 * Планировщика в сервере нет ни в одной сборке, и заводить его ради этого незачем: уборка
 * запускается там же, где мусор появляется, — при создании новой песочницы.
 */
@OptIn(ExperimentalTime::class)
class DemoSandboxCleaner(
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend fun sweep(now: Instant = Clock.System.now()) {
        val expiredBefore = now.epochSeconds - SANDBOX_LIFETIME_SECONDS

        userRepository
            .findByUsernamePrefix(DEMO_USERNAME_PREFIX)
            .filter { user ->
                val createdAt = user.id.objectIdCreatedAtSeconds() ?: return@filter false
                createdAt < expiredBefore
            }.forEach { user ->
                // Сначала транзакции, потом владелец. Обрыв на середине в обратном порядке
                // оставил бы транзакции без пользователя — их больше нечем найти и нечем
                // удалить. В этом порядке недоубранная песочница просто попадёт под следующую
                // уборку.
                transactionRepository.deleteByUser(user.id)
                userRepository.delete(user.id)
            }
    }
}
