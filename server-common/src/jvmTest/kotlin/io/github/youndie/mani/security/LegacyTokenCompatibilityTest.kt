package io.github.youndie.mani.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.youndie.mani.config.JWTConfig
import kotlinx.coroutines.test.runTest
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Совместимость с токенами, выданными до перехода.
 *
 * До этой работы токены подписывал `com.auth0:java-jwt`, и refresh-токены со сроком в месяц
 * **лежат в базе стенда**. Если новая проверка их не примет, каждый, кто зашёл до выката, будет
 * разлогинен — и узнаем мы об этом не из сборки, а из жалоб.
 *
 * Поэтому эталон здесь настоящий: токен выдаётся той самой библиотекой и проверяется нашим
 * [TokenService]. Обратная сторона — наш токен, разобранный `java-jwt`, — проверяется тем же
 * тестом: пока на стенде живут обе сборки, токен одной обязан работать в другой.
 *
 * `java-jwt` подключён только в тестах и в продукт не входит.
 */
class LegacyTokenCompatibilityTest {
    private val config =
        JWTConfig(
            secret = "secret",
            audience = "jwt-audience",
            issuer = "jwt-issuer",
            expirationSeconds = 3600,
        )
    private val service = TokenService(config)
    private val algorithm = Algorithm.HMAC256(config.secret)

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "фикстура теста строит момент относительно сейчас",
    )
    private fun legacyToken(
        id: String,
        username: String,
        expiresAt: Date = Date(System.currentTimeMillis() + 3_600_000),
    ): String = JWT
        .create()
        .withSubject("Authentication")
        .withAudience(config.audience)
        .withIssuer(config.issuer)
        .withClaim("id", id)
        .withClaim("username", username)
        .withExpiresAt(expiresAt)
        .sign(algorithm)

    /**
     * Старый токен принимается как **refresh** — и только так.
     *
     * Вида в нём не записано: claim `kind` появился позже. В базе стенда лежат именно
     * refresh-токены, поэтому отвергнуть их значит разлогинить всех, кто зашёл до выката.
     */
    @Test
    fun acceptsTokenIssuedByJavaJwt() = runTest {
        val claims = assertNotNull(service.verify(legacyToken("64b7f0c2e1a2b3c4d5e6f708", "vasya"), TokenKind.Refresh))

        assertEquals("64b7f0c2e1a2b3c4d5e6f708", claims.id)
        assertEquals("vasya", claims.username)
    }

    /**
     * И не принимается как access, хотя подпись у него верная.
     *
     * Иначе поблажка ради старых записей открыла бы ровно ту дыру, которую закрывает claim
     * `kind`: месячный refresh, предъявленный вместо часового access. Держатель старого токена
     * получит 401 один раз, клиент сходит за парой на `/auth/refresh` и продолжит с новой.
     */
    @Test
    fun rejectsLegacyTokenWhereAccessIsExpected() = runTest {
        assertNull(service.verify(legacyToken("64b7f0c2e1a2b3c4d5e6f708", "vasya"), TokenKind.Access))
    }

    @Test
    fun rejectsExpiredTokenIssuedByJavaJwt() = runTest {
        @Suppress(
            "ktlint:kapkan:wall-clock",
            "фикстура теста строит момент относительно сейчас",
        )
        val expired = legacyToken("1", "u", expiresAt = Date(System.currentTimeMillis() - 1_000))

        assertNull(service.verify(expired, TokenKind.Refresh))
    }

    @Test
    fun javaJwtAcceptsOurToken() = runTest {
        val ours = service.issue(id = "64b7f0c2e1a2b3c4d5e6f708", username = "vasya", kind = TokenKind.Access)

        val decoded =
            JWT
                .require(algorithm)
                .withAudience(config.audience)
                .withIssuer(config.issuer)
                .build()
                .verify(ours)

        assertEquals("64b7f0c2e1a2b3c4d5e6f708", decoded.getClaim("id").asString())
        assertEquals("vasya", decoded.getClaim("username").asString())
        assertEquals("Authentication", decoded.subject)
    }
}
