package io.github.youndie.mani.security

import io.github.youndie.mani.config.JWTConfig
import kotlinx.coroutines.test.runTest
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
        val token = service.issue(id = "64b7f0c2e1a2b3c4d5e6f708", username = "vasya", kind = TokenKind.Access)

        val claims = assertNotNull(service.verify(token, TokenKind.Access))
        assertEquals("64b7f0c2e1a2b3c4d5e6f708", claims.id)
        assertEquals("vasya", claims.username)
    }

    @Test
    fun tokenHasThreeBase64UrlParts() = runTest {
        val token = service.issue(id = "1", username = "u", kind = TokenKind.Access)
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
        val first = service.issue(id = "1", username = "u", kind = TokenKind.Access)
        val second = service.issue(id = "1", username = "u", kind = TokenKind.Access)

        assertTrue(first != second)
        assertNotNull(service.verify(first, TokenKind.Access))
        assertNotNull(service.verify(second, TokenKind.Access))
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
        val token = service.issue(id = "1", username = "u", kind = TokenKind.Access)
        val signature = token.substringAfterLast('.')
        val broken = signature.replaceRange(0, 1, if (signature[0] == 'A') "B" else "A")

        assertNull(service.verify(token.substringBeforeLast('.') + "." + broken, TokenKind.Access))
    }

    /** Настоящая подмена: тело переписано, подпись осталась прежней. */
    @Test
    fun rejectsTamperedPayload() = runTest {
        val mine = service.issue(id = "mine", username = "u", kind = TokenKind.Access)
        val foreign = service.issue(id = "foreign", username = "u", kind = TokenKind.Access)

        val parts = mine.split('.')
        val spliced = parts[0] + "." + foreign.split('.')[1] + "." + parts[2]

        assertNull(service.verify(spliced, TokenKind.Access))
    }

    @Test
    fun rejectsTokenSignedWithAnotherSecret() = runTest {
        val other = TokenService(config.copy(secret = "another"))

        assertNull(service.verify(other.issue(id = "1", username = "u", kind = TokenKind.Access), TokenKind.Access))
    }

    @Test
    fun rejectsForeignIssuerAndAudience() = runTest {
        assertNull(
            service.verify(
                TokenService(config.copy(issuer = "someone-else")).issue("1", "u", TokenKind.Access),
                TokenKind.Access,
            ),
        )
        assertNull(
            service.verify(
                TokenService(config.copy(audience = "someone-else")).issue("1", "u", TokenKind.Access),
                TokenKind.Access,
            ),
        )
    }

    @Test
    fun rejectsExpiredToken() = runTest {
        @Suppress(
            "ktlint:kapkan:wall-clock",
            "фикстура теста строит момент относительно сейчас",
        )
        val expired =
            service.issue(
                id = "1",
                username = "u",
                kind = TokenKind.Access,
                expiration = Clock.System.now().minus(1.hours),
            )

        assertNull(service.verify(expired, TokenKind.Access))
    }

    @Test
    fun rejectsGarbage() = runTest {
        assertNull(service.verify("", TokenKind.Access))
        assertNull(service.verify("not-a-token", TokenKind.Access))
        assertNull(service.verify("a.b.c", TokenKind.Access))
    }

    /**
     * Вид токена проверяется, а не просто записывается.
     *
     * Refresh живёт месяц и предъявляется раз в час; access ходит в каждом запросе и живёт час.
     * Пока проверка их не различала, предъявленный вместо access refresh давал месячный пропуск,
     * и короткий срок access-токена не значил ничего.
     */
    @Test
    fun refreshTokenIsRefusedWhereAccessIsExpected() = runTest {
        val refresh = service.issue(id = "1", username = "u", kind = TokenKind.Refresh)

        assertNull(service.verify(refresh, TokenKind.Access))
        assertNotNull(service.verify(refresh, TokenKind.Refresh))
    }

    /** И обратно: access-токен не годится для обновления сессии. */
    @Test
    fun accessTokenIsRefusedWhereRefreshIsExpected() = runTest {
        val access = service.issue(id = "1", username = "u", kind = TokenKind.Access)

        assertNull(service.verify(access, TokenKind.Refresh))
        assertNotNull(service.verify(access, TokenKind.Access))
    }
}
