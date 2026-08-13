package ru.workinprogress.feature.demo.data

import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.auth.data.AuthService
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.transaction.Category
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.data.UserRepository
import ru.workinprogress.mani.demo.DemoSeed
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
) {
    /** @return токены к заведённой песочнице либо `null`, если хранилище отказало */
    suspend fun createSandbox(): Tokens? {
        val credentials = freeCredentials() ?: return null
        val userId = userRepository.save(credentials) ?: return null

        // Категории заводятся первыми: у правила есть только имя категории, а транзакции нужен
        // идентификатор, который появляется в момент создания.
        val categories =
            DemoSeed.categories.associateWith { name ->
                categoryRepository.create(Category(id = "", name = name), userId)
            }

        DemoSeed
            .transactions(category = { name -> categories.getValue(name) })
            .forEach { transaction -> transactionRepository.create(transaction, userId) }

        return authService.authenticate(credentials)
    }

    /**
     * Имя вида `demo-1a2b3c4d`. Совпадение практически невероятно, но пользователь с занятым
     * именем не сохранится, а посетитель увидит отказ на пустом месте, — поэтому имя проверяется,
     * и попыток несколько.
     */
    private suspend fun freeCredentials(): LoginParams? =
        (1..CREDENTIALS_ATTEMPTS)
            .asSequence()
            .map { LoginParams(name = "demo-${randomHex(NAME_BYTES)}", password = randomHex(PASSWORD_BYTES)) }
            .firstOrNull { userRepository.findByUsername(it.name) == null }

    private fun randomHex(bytes: Int): String =
        (1..bytes).joinToString("") {
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
