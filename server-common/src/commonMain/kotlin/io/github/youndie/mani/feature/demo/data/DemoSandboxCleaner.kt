package io.github.youndie.mani.feature.demo.data

import io.github.youndie.mani.feature.transaction.data.TransactionRepository
import io.github.youndie.mani.feature.user.data.UserRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Префикс имени, по которому песочница отличается от настоящего пользователя. */
const val DEMO_USERNAME_PREFIX = "demo-"

/** Сутки. Дольше песочницу никто не смотрит, а ссылку на витрину открывают заново. */
const val SANDBOX_LIFETIME_SECONDS = 24 * 60 * 60L

/**
 * Сколько песочниц может жить одновременно.
 *
 * У базы стенда 256 МиБ диска и 128 МиБ памяти, а `POST /demo` не требует ни ввода, ни входа:
 * цикл запросов заводил пользователя с семью правилами столько раз, сколько успеет. Уборка от
 * этого не спасает — она уносит то, чему больше суток, а заполнить диск можно за минуты.
 *
 * Число с запасом: живых песочниц на витрине единицы, и потолок должен отсекать поток, а не
 * посетителя.
 */
const val MAX_LIVE_SANDBOXES = 500

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
    /**
     * @return сколько песочниц осталось жить после уборки. Считается тем же списком, который она
     *   и так читает: отдельный запрос за числом ходил бы в базу второй раз за теми же данными.
     */
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "`now` и есть порт: часы входят одним умолчанием, тест его подменяет",
    )
    suspend fun sweep(now: Instant = Clock.System.now()): Int {
        val expiredBefore = now.epochSeconds - SANDBOX_LIFETIME_SECONDS

        val all = userRepository.findByUsernamePrefix(DEMO_USERNAME_PREFIX)
        val expired = all
            .filter { user ->
                val createdAt = user.id.objectIdCreatedAtSeconds() ?: return@filter false
                createdAt < expiredBefore
            }

        expired
            .forEach { user ->
                // Сначала транзакции, потом владелец. Обрыв на середине в обратном порядке
                // оставил бы транзакции без пользователя — их больше нечем найти и нечем
                // удалить. В этом порядке недоубранная песочница просто попадёт под следующую
                // уборку.
                transactionRepository.deleteByUser(user.id)
                userRepository.delete(user.id)
            }

        return all.size - expired.size
    }
}
