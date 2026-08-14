package ru.workinprogress.mani.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.test.runTest
import ru.workinprogress.mani.config.JWTConfig
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

    @Test
    fun acceptsTokenIssuedByJavaJwt() = runTest {
        val claims = assertNotNull(service.verify(legacyToken("64b7f0c2e1a2b3c4d5e6f708", "vasya")))

        assertEquals("64b7f0c2e1a2b3c4d5e6f708", claims.id)
        assertEquals("vasya", claims.username)
    }

    @Test
    fun rejectsExpiredTokenIssuedByJavaJwt() = runTest {
        val expired = legacyToken("1", "u", expiresAt = Date(System.currentTimeMillis() - 1_000))

        assertNull(service.verify(expired))
    }

    @Test
    fun javaJwtAcceptsOurToken() = runTest {
        val ours = service.issue(id = "64b7f0c2e1a2b3c4d5e6f708", username = "vasya")

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
