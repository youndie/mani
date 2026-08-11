package ru.workinprogress.mani.security

import kotlinx.coroutines.test.runTest
import ru.workinprogress.feature.auth.data.hashing.SaltedHash
import ru.workinprogress.feature.auth.data.hashing.Sha256HashingService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashingServiceTest {
    private val service = Sha256HashingService()

    /**
     * Главный тест этого класса: запись, сделанная **прежней** JVM-реализацией на
     * `commons-codec`, обязана проверяться новой.
     *
     * Вектор посчитан независимо (`shasum -a 256` по строке `соль + пароль`), а не снят с самой
     * реализации: иначе тест доказывал бы только внутреннюю непротиворечивость, а нужно
     * совпадение с тем, что уже лежит в базе стенда.
     */
    @Test
    fun acceptsHashProducedByPreviousImplementation() =
        runTest {
            val stored =
                SaltedHash(
                    hash = "0a8a81d7b1556030afa25a65c7ad7c38de71441b0acc06d46428c536fa17023b",
                    salt = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
                )

            assertTrue(service.verify("hunter2", stored))
            assertFalse(service.verify("hunter3", stored))
        }

    @Test
    fun generatedHashVerifies() =
        runTest {
            val salted = service.generateSaltedHash("пароль с юникодом")

            assertEquals(64, salted.salt.length, "соль — 32 байта в hex")
            assertEquals(64, salted.hash.length, "sha256 в hex")
            assertTrue(service.verify("пароль с юникодом", salted))
            assertFalse(service.verify("другой", salted))
        }

    /** Две записи одного пароля обязаны отличаться — иначе соль не работает. */
    @Test
    fun saltIsRandom() =
        runTest {
            val first = service.generateSaltedHash("one")
            val second = service.generateSaltedHash("one")

            assertFalse(first.salt == second.salt)
            assertFalse(first.hash == second.hash)
        }
}
