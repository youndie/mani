package io.github.youndie.mani.feature.main.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Через столько повторяется неудавшаяся загрузка. */
const val RETRY_SECONDS = 8

/** Столько раз подряд. Дальше экран перестаёт дёргаться и ждёт человека. */
const val MAX_RETRIES = 3

/**
 * Повтор неудавшейся загрузки с обратным отсчётом.
 *
 * Молча повторять нельзя: экран выглядел бы застывшим. Секунды на экране — обещание, что
 * приложение занято делом, а не ждёт, пока на него нажмут.
 *
 * Попытки не бесконечны: если сервера нет и через три захода, экран останавливается. Бесконечный
 * цикл к тому же держал бы приложение занятым в фоне.
 *
 * Общий на два экрана. Главный умел это с самого начала, история — нет: при том же отказе она
 * показывала строку текста и не пыталась больше ничего. Два экрана одного приложения не должны
 * по-разному переживать одну и ту же пропажу сети, а копия этого счётчика разошлась бы с
 * оригиналом на первой же правке.
 *
 * @param onCountdown вызывается каждую секунду отсчёта: экран сам решает, куда положить число
 * @param load что повторять
 */
class RetrySchedule(
    private val scope: CoroutineScope,
    private val onCountdown: (secondsLeft: Int) -> Unit,
    private val load: suspend () -> Unit,
) {
    private var job: Job? = null
    private var attempt = 0

    /** Загрузка удалась: следующая беда начинает счёт заново. */
    fun succeeded() {
        attempt = 0
    }

    /** Назначить повтор, если попытки ещё остались. */
    fun schedule() {
        if (attempt >= MAX_RETRIES) return
        attempt++

        job?.cancel()
        job = scope.launch {
            for (left in RETRY_SECONDS downTo 1) {
                onCountdown(left)
                delay(1.seconds)
            }
            load()
        }
    }

    /** Повтор вручную: отсчёт снимается, а счётчик обнуляется — нажали руками, значит ждать готовы. */
    fun retryNow() {
        job?.cancel()
        attempt = 0
        scope.launch { load() }
    }
}
