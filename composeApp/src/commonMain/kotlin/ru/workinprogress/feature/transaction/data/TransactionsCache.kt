package ru.workinprogress.feature.transaction.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import ru.workinprogress.feature.transaction.Transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Последний удачный ответ сервера и когда он был получен. */
data class CachedTransactions(
    val transactions: List<Transaction>,
    val takenAt: Instant,
)

/**
 * Последний известный список правил.
 *
 * Без него приложение без сети показывает пустой экран, хотя данные не меняются сами по себе:
 * правила — это не лента событий, вчерашний список остаётся верным и сегодня. Показать его с
 * отметкой времени честнее, чем не показать ничего.
 *
 * Хранится в тех же настройках, что и токены, — отдельного хранилища ради списка из десятка
 * записей заводить незачем.
 */
@OptIn(ExperimentalTime::class)
class TransactionsCache(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun save(transactions: List<Transaction>) {
        settings.putString(KEY_DATA, json.encodeToString(transactions))
        settings.putLong(KEY_TAKEN_AT, Clock.System.now().toEpochMilliseconds())
    }

    /** `null` — кэша нет либо он испорчен: повод сходить в сеть, а не падать. */
    fun load(): CachedTransactions? {
        val raw = settings.getStringOrNull(KEY_DATA) ?: return null
        val takenAt = settings.getLongOrNull(KEY_TAKEN_AT) ?: return null

        return try {
            CachedTransactions(
                transactions = json.decodeFromString(raw),
                takenAt = Instant.fromEpochMilliseconds(takenAt),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        settings.remove(KEY_DATA)
        settings.remove(KEY_TAKEN_AT)
    }

    private companion object {
        const val KEY_DATA = "transactions.cache"
        const val KEY_TAKEN_AT = "transactions.cache.takenAt"
    }
}
