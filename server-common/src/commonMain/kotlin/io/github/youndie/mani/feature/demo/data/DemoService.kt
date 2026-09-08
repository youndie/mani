package io.github.youndie.mani.feature.demo.data

import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.feature.auth.Tokens
import io.github.youndie.mani.feature.auth.data.AuthService
import io.github.youndie.mani.feature.category.CategoryRepository
import io.github.youndie.mani.feature.demo.DemoSeed
import io.github.youndie.mani.feature.transaction.Category
import io.github.youndie.mani.feature.transaction.data.TransactionRepository
import io.github.youndie.mani.feature.user.data.UserRepository
import io.github.youndie.mani.utilz.suspendRunCatching
import kotlin.random.Random

/**
 * Разворачивает песочницу: одноразовый пользователь с данными из [DemoSeed] и токены к нему.
 *
 * Нужна затем, чтобы посетитель витрины не входил в общий аккаунт. Общий аккаунт означает, что
 * любой может править и удалять чужое, — именно так демо и пришло к данным «mani minuz −3 $
 * каждый день» и строке «no zero events» вместо прогноза.
 *
 * Платформенного здесь нет ничего: класс лежит в общей части и достаётся обеим сборкам сервера
 * из одного кода — нативной реализации песочницы не существует и не должно появиться.
 */
class DemoService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val authService: AuthService,
    private val cleaner: DemoSandboxCleaner,
    /** Параметром, а не константой в теле: иначе потолок нечем проверить, кроме как достичь его. */
    private val maxLiveSandboxes: Int = MAX_LIVE_SANDBOXES,
) {
    /** Чем кончилась попытка развернуть песочницу. */
    sealed interface Outcome {
        data class Created(val tokens: Tokens) : Outcome

        /** Мест нет: живых песочниц столько, сколько стенд готов держать. */
        data object NoRoom : Outcome

        /** Хранилище отказало. */
        data object Refused : Outcome
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "отказ уборки не должен стоить посетителю входа — причина расписана выше",
    )
    suspend fun createSandbox(): Outcome {
        // Отказ уборки не должен стоить посетителю входа: мусор подождёт следующего вызова,
        // а пустой экран вместо витрины — нет. Логгера в общей части нет ни у одной сборки,
        // поэтому отказ именно проглатывается, а не пишется в никуда.
        //
        // Именно отказ, но не отмена: обычный `runCatching` проглотил бы и её, и метод
        // продолжил бы заводить песочницу для клиента, который уже отвалился.
        val live = suspendRunCatching { cleaner.sweep() }.getOrNull()

        // Не сосчитали — не запрещаем. Уборка отказала редко, а закрытая витрина из-за сбоя
        // подсчёта — это отказ по причине, к посетителю не относящейся.
        if (live != null && live >= maxLiveSandboxes) return Outcome.NoRoom

        val credentials = freeCredentials() ?: return Outcome.Refused
        val userId = userRepository.save(credentials) ?: return Outcome.Refused

        seed(userId)

        return authService.authenticate(credentials)?.let(Outcome::Created) ?: Outcome.Refused
    }

    /**
     * Кладёт данные сида пользователю.
     *
     * Категории заводятся первыми: у правила есть только имя категории, а транзакции нужен
     * идентификатор, который появляется в момент создания.
     */
    suspend fun seed(userId: String) {
        val categories =
            DemoSeed.categories.associateWith { name ->
                categoryRepository.create(Category(id = "", name = name), userId)
            }

        DemoSeed
            .transactions(category = { name -> categories.getValue(name) })
            .forEach { transaction -> transactionRepository.create(transaction, userId) }
    }

    /**
     * Имя вида `demo-1a2b3c4d`. Совпадение практически невероятно, но пользователь с занятым
     * именем не сохранится, а посетитель увидит отказ на пустом месте, — поэтому имя проверяется,
     * и попыток несколько.
     */
    private suspend fun freeCredentials(): LoginParams? = (1..CREDENTIALS_ATTEMPTS)
        .asSequence()
        .map {
            LoginParams(
                name = DEMO_USERNAME_PREFIX + randomHex(NAME_BYTES),
                password = randomHex(PASSWORD_BYTES),
            )
        }
        .firstOrNull { userRepository.findByUsername(it.name) == null }

    private fun randomHex(bytes: Int): String = (1..bytes).joinToString("") {
        Random
            .nextInt(BYTE_VALUES)
            .toString(HEX)
            .padStart(2, '0')
    }

    private companion object {
        const val CREDENTIALS_ATTEMPTS = 5
        const val NAME_BYTES = 4
        const val PASSWORD_BYTES = 16
        const val BYTE_VALUES = 256
        const val HEX = 16
    }
}
