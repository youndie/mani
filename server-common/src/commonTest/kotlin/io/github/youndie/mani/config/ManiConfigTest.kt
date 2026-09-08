package io.github.youndie.mani.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Секрет подписи не берётся из исходников.
 *
 * Проверяется [signingSecret], а не `ManiConfig.fromEnv()`: второе читает окружение машины, на
 * которой идёт прогон, и тест был бы зелёным по разным причинам на разных машинах — а на той,
 * где `JWT_SECRET` задан, не проверял бы вообще ничего.
 */
class ManiConfigTest {
    @Test
    fun anAbsentSecretBecomesARandomOne() {
        val first = signingSecret(null)
        val second = signingSecret(null)

        // Строка `secret` была умолчанием и лежала в исходниках открытым текстом: подписать
        // токен ею мог кто угодно.
        assertNotEquals("secret", first)
        // Разные при каждом обращении — иначе «случайный» секрет был бы константой в другом
        // месте, и подделать его стало бы делом одного взгляда в код.
        assertNotEquals(first, second)
        assertEquals(64, first.length, "32 байта в hex")
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun aConfiguredSecretIsTakenAsIs() {
        assertEquals("from-the-environment", signingSecret("from-the-environment"))
    }
}
