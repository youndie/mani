package ru.workinprogress.mani.security

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond

/** Кто пришёл. Кладётся в call после успешной проверки токена. */
data class ManiPrincipal(val id: String, val username: String)

/**
 * Проверка Bearer-токена — своя, вместо `ktor-server-auth-jwt`.
 *
 * Плагин Ktor держится за `com.auth0:java-jwt` и существует только на JVM. Здесь ровно то же
 * поведение поверх [TokenService]: неверный или просроченный токен даёт 401 с прежним текстом,
 * а не 500 и не пустой ответ.
 */
class ManiJwtProvider internal constructor(config: Config) : AuthenticationProvider(config) {
    private val tokenService = config.tokenService

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val header = context.call.request.parseAuthorizationHeader()
        val token = (header as? HttpAuthHeader.Single)?.takeIf {
            it.authScheme.equals("Bearer", ignoreCase = true)
        }?.blob

        val claims = token?.let { tokenService.verify(it) }
        if (claims != null) {
            context.principal(ManiPrincipal(id = claims.id, username = claims.username))
            return
        }

        context.challenge(CHALLENGE_KEY, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            challenge.complete()
        }
    }

    class Config internal constructor(name: String, internal val tokenService: TokenService) :
        AuthenticationProvider.Config(name)

    internal companion object {
        const val CHALLENGE_KEY = "ManiJwtAuth"
    }
}

fun AuthenticationConfig.maniJwt(name: String, tokenService: TokenService) {
    register(ManiJwtProvider(ManiJwtProvider.Config(name, tokenService)))
}
