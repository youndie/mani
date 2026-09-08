package io.github.youndie.mani.feature.auth.data

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TokenRepository {
    fun getToken(): BearerTokens
    fun set(accessToken: String = getToken().accessToken, refreshToken: String = getToken().refreshToken.orEmpty())

    fun observeToken(): StateFlow<BearerTokens>

    /**
     * Сессия кончилась не по воле человека: сервер отверг refresh-токен.
     *
     * Событие, а не состояние. Подписывать навигацию на сам токен нельзя — так уже делали, и
     * каждый его приход пересобирал граф, сбрасывая экран на точку входа; подробности в
     * `ManiAppNavHost`.
     */
    val expired: Flow<Unit>

    /** Отметить, что сессия кончилась: сбрасывает токены и поднимает [expired]. */
    fun expire()
}
