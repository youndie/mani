package ru.workinprogress.mani.security

import kotlinx.coroutines.test.runTest
import ru.workinprogress.mani.config.JWTConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TokenServiceTest {
    private val config =
        JWTConfig(
            secret = "s3cret",
            audience = "mani-audience",
            issuer = "mani-issuer",
            expirationSeconds = 3600,
        )
    private val service = TokenService(config)

    @Test
    fun issuedTokenVerifies() = runTest {
        val token = service.issue(id = "64b7f0c2e1a2b3c4d5e6f708", username = "vasya")

        val claims = assertNotNull(service.verify(token))
        assertEquals("64b7f0c2e1a2b3c4d5e6f708", claims.id)
        assertEquals("vasya", claims.username)
    }

    @Test
    fun tokenHasThreeBase64UrlParts() = runTest {
        val token = service.issue(id = "1", username = "u")
        val parts = token.split('.')

        assertEquals(3, parts.size)
        // Выравнивание `=` в JWT запрещено, а лишний символ меняет байты под подписью.
        assertTrue(parts.none { it.contains('=') })
    }

    /**
     * Два токена, выданных подряд, обязаны отличаться.
     *
     * Без этого обновление сессии возвращало **тот же** refresh-токен: `exp` в JWT хранится
     * в секундах, а остальные claim'ы у пары совпадают. Ротации при этом нет — украденный токен
     * остаётся годным, — и снаружи всё выглядит исправным.
     */
    @Test
    fun issuedTokensAreUnique() = runTest {
        val first = service.issue(id = "1", username = "u")
        val second = service.issue(id = "1", username = "u")

        assertTrue(first != second)
        assertNotNull(service.verify(first))
        assertNotNull(service.verify(second))
    }

    /**
     * Портится символ **в середине** подписи, а не последний.
     *
     * Подпись HMAC-SHA256 — 32 байта, то есть 43 символа base64url, и последний из них несёт
     * всего четыре значащих бита: два разных символа на этом месте декодируются в одни и те же
     * байты. Тест, портивший последний символ, проходил через раз — ровно с тех пор, как
     * в токене появился случайный `jti`.
     */
    @Test
    fun rejectsTamperedSignature() = runTest {
        val token = service.issue(id = "1", username = "u")
        val signature = token.substringAfterLast('.')
        val broken = signature.replaceRange(0, 1, if (signature[0] == 'A') "B" else "A")

        assertNull(service.verify(token.substringBeforeLast('.') + "." + broken))
    }

    /** Настоящая подмена: тело переписано, подпись осталась прежней. */
    @Test
    fun rejectsTamperedPayload() = runTest {
        val mine = service.issue(id = "mine", username = "u")
        val foreign = service.issue(id = "foreign", username = "u")

        val parts = mine.split('.')
        val spliced = parts[0] + "." + foreign.split('.')[1] + "." + parts[2]

        assertNull(service.verify(spliced))
    }

    @Test
    fun rejectsTokenSignedWithAnotherSecret() = runTest {
        val other = TokenService(config.copy(secret = "another"))

        assertNull(service.verify(other.issue(id = "1", username = "u")))
    }

    @Test
    fun rejectsForeignIssuerAndAudience() = runTest {
        assertNull(service.verify(TokenService(config.copy(issuer = "someone-else")).issue("1", "u")))
        assertNull(service.verify(TokenService(config.copy(audience = "someone-else")).issue("1", "u")))
    }

    @Test
    fun rejectsExpiredToken() = runTest {
        val expired = service.issue(id = "1", username = "u", expiration = Clock.System.now().minus(1.hours))

        assertNull(service.verify(expired))
    }

    @Test
    fun rejectsGarbage() = runTest {
        assertNull(service.verify(""))
        assertNull(service.verify("not-a-token"))
        assertNull(service.verify("a.b.c"))
    }
}
