package io.github.youndie.mani.utilz

import kotlin.coroutines.cancellation.CancellationException

/**
 * `runCatching` для корутин: отмена проходит насквозь, а не превращается в `Result.failure`.
 *
 * Обычный `runCatching` ловит и [CancellationException] — отменённая корутина после этого
 * продолжает выполняться, а вызывающий получает отмену под видом ошибки: экран рисует её
 * как сбой сети, сервер — как 401.
 */
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
