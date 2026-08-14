package ru.workinprogress.feature.auth.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.feature.auth.Tokens
import ru.workinprogress.feature.user.User
import ru.workinprogress.feature.user.data.TokenRepository
import ru.workinprogress.feature.user.data.UserRepository
import ru.workinprogress.mani.security.TokenService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Логин и обновление токенов.
 *
 * Обычный класс в общей части, без expect/actual: платформенного здесь ничего нет — вся разница
 * между сборками спрятана за [TokenService] и репозиториями.
 */
@OptIn(ExperimentalTime::class)
class AuthService(
    private val userRepository: UserRepository,
    private val tokenRepository: TokenRepository,
    private val tokenService: TokenService,
) {
    suspend fun authenticate(loginRequest: LoginParams): Tokens? {
        val foundUser = userRepository.findUserByCredentials(loginRequest) ?: return null
        return newTokens(foundUser).also { tokens ->
            tokenRepository.addToken(userId = foundUser.id, token = tokens.refreshToken)
        }
    }

    suspend fun refreshToken(refreshToken: String): Tokens? {
        val claims = tokenService.verify(refreshToken) ?: return null

        // Подписи мало: предъявленный refresh-токен обязан ещё и лежать в базе. Иначе однажды
        // отозванный токен продолжал бы работать до самого истечения.
        val foundUser = tokenRepository.findUserByToken(refreshToken) ?: return null
        if (claims.username != foundUser.username) return null

        tokenRepository.removeToken(refreshToken, foundUser.id)

        return newTokens(foundUser).also {
            tokenRepository.addToken(token = it.refreshToken, userId = foundUser.id)
        }
    }

    private suspend fun newTokens(user: User): Tokens = Tokens(
        accessToken = tokenService.issue(user.id, user.username),
        refreshToken =
        tokenService.issue(
            user.id,
            user.username,
            expiration =
            Clock.System
                .now()
                .plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()),
        ),
    )
}
