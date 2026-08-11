package ru.workinprogress.feature.auth.data.hashing

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import ru.workinprogress.mani.security.constantTimeEquals
import ru.workinprogress.mani.security.toHex

data class SaltedHash(
    val hash: String,
    val salt: String,
)

interface HashingService {
    suspend fun generateSaltedHash(
        value: String,
        saltLength: Int = 32,
    ): SaltedHash

    suspend fun verify(
        value: String,
        saltedHash: SaltedHash,
    ): Boolean
}

/**
 * Пароли: соль из CSPRNG в hex, хеш — `sha256(hex(соль) + пароль)`, тоже в hex.
 *
 * **Формат воспроизведён байт в байт** за прежней JVM-реализацией на `commons-codec`, и это
 * главное требование к этому классу, а не его устройство: в базе стенда уже лежат такие записи,
 * и любое отклонение — конкатенация в другом порядке, соль в base64, регистр hex — означало бы,
 * что ни один существующий пользователь больше не войдёт. Проверяется тестом на записи,
 * снятой с прежней реализации.
 *
 * SHA-256 без итераций — наследство, а не решение: для паролей, придуманных человеком, нужен
 * медленный хеш (PBKDF2/Argon2). Менять его надо вместе с миграцией существующих записей, то
 * есть отдельной задачей, и формат для этого придётся сделать самоописывающим.
 */
class Sha256HashingService : HashingService {
    private val sha256 = CryptographyProvider.Default.get(SHA256).hasher()

    override suspend fun generateSaltedHash(
        value: String,
        saltLength: Int,
    ): SaltedHash {
        val saltAsHex = CryptographyRandom.nextBytes(saltLength).toHex()
        return SaltedHash(
            hash = sha256Hex(saltAsHex + value),
            salt = saltAsHex,
        )
    }

    override suspend fun verify(
        value: String,
        saltedHash: SaltedHash,
    ): Boolean = constantTimeEquals(saltedHash.hash, sha256Hex(saltedHash.salt + value))

    private suspend fun sha256Hex(value: String): String = sha256.hash(value.encodeToByteArray()).toHex()
}
