package ru.workinprogress.feature.user.data

import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.user.User

/**
 * Порт хранилища пользователей.
 *
 * Интерфейс живёт в общей части, реализации — в сборочных модулях: официальный драйвер
 * существует только на JVM, mongkn — только под linuxX64, и общего типа документа у них нет.
 */
interface UserRepository {
    /** @return идентификатор созданного пользователя либо `null`, если запись не удалась */
    suspend fun save(user: LoginParams): String?

    suspend fun findUserByCredentials(credentials: LoginParams): User?

    suspend fun findUserById(id: String): User?

    suspend fun findByUsername(userName: String): User?

    /**
     * Пользователи, чьё имя начинается с префикса. Нужно уборке песочниц — других поводов
     * искать пользователей пачкой в продукте нет.
     */
    suspend fun findByUsernamePrefix(prefix: String): List<User>

    suspend fun delete(userId: String)
}

/** Refresh-токены пользователя. Лежат массивом в его же документе — так было и на JVM. */
interface TokenRepository {
    suspend fun addToken(token: String, userId: String)

    suspend fun findUserByToken(refreshToken: String): User?

    suspend fun removeToken(token: String, userId: String)
}
