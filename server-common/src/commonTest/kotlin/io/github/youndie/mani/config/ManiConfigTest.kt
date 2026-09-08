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

    /**
     * Логин и пароль доезжают до строки подключения.
     *
     * Раньше поля читались из окружения и никуда не попадали: задавший `MONGO_PASSWORD` получал
     * подключение без пароля, и понять это по поведению было нельзя.
     */
    @Test
    fun credentialsReachTheConnectionString() {
        val withAuth = MongoConfig(userName = "mani", password = "hunter22", host = "db:27017")

        assertEquals("mongodb://mani:hunter22@db:27017/?w=majority&appName=Mani", withAuth.connectionString)
    }

    /** Пустой логин — строка ровно та же, что была до этого: у mongod стенда аутентификации нет. */
    @Test
    fun noCredentialsMeansNoChange() {
        assertEquals(
            "mongodb://localhost/?w=majority&appName=Mani",
            MongoConfig(host = "localhost").connectionString,
        )
    }

    /** Пароль со служебными символами экранируется, иначе адрес разбирается не туда. */
    @Test
    fun aPasswordWithSeparatorsIsEscaped() {
        val tricky = MongoConfig(userName = "mani", password = "p@ss:word", host = "db")

        assertEquals("mongodb://mani:p%40ss%3Aword@db/?w=majority&appName=Mani", tricky.connectionString)
    }
}
