package io.github.youndie.mani.feature.auth

import io.github.youndie.mani.feature.auth.data.TokenStorageImpl
import io.ktor.client.plugins.auth.providers.BearerTokens
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Файл сессии на десктопе.
 *
 * Он лежал `session.txt` в текущем каталоге — там, откуда запустили приложение, — с правами по
 * умолчанию. Токен доступа даёт те же права, что пароль.
 */
class TokenStorageImplTest {

    private val file = createTempDirectory("mani-session") / "nested" / "session"

    @Test
    fun savedTokensComeBack() {
        val storage = TokenStorageImpl(file)

        storage.save(BearerTokens("access", "refresh"))

        assertEquals(BearerTokens("access", "refresh").accessToken, storage.load()?.accessToken)
        assertEquals("refresh", storage.load()?.refreshToken)
    }

    /** Каталог создаётся сам: до первого входа его нет ни у кого. */
    @Test
    fun theFileIsReadableOnlyByItsOwner() {
        TokenStorageImpl(file).save(BearerTokens("access", "refresh"))

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file),
        )
    }

    @Test
    fun aMissingFileIsNotAFailure() {
        assertNull(TokenStorageImpl(file).load())
    }

    /** Оборванная запись — одна строка вместо двух: это не токен, а мусор. */
    @Test
    fun aTruncatedFileIsIgnored() {
        file.parent.let { Files.createDirectories(it) }
        file.writeText("only-one-line")

        assertNull(TokenStorageImpl(file).load())
    }
}
