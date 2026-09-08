package io.github.youndie.mani.feature.auth.data

import io.ktor.client.plugins.auth.providers.BearerTokens
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Токены сессии на диске.
 *
 * Файл лежит в каталоге настроек пользователя и доступен только ему. Раньше это был `session.txt`
 * в ТЕКУЩЕМ каталоге — то есть там, откуда приложение запустили: в репозитории, в загрузках, где
 * угодно, с правами по умолчанию и с шансом уехать в git. Токен доступа даёт ровно те же права,
 * что пароль, и хранить его так — то же, что положить пароль рядом с исходниками.
 *
 * @param file куда писать. Параметром — чтобы тест писал во временный каталог, а не в настоящий
 *   профиль того, кто гоняет прогон.
 */
class TokenStorageImpl(private val file: Path = defaultSessionFile()) : TokenStorage {

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "нет файла с токеном — обычное состояние первого запуска, а не отказ",
    )
    override fun load(): BearerTokens? = try {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }?.split(SEPARATOR)
        // Оборванная запись оставляет в файле одну строку вместо двух. Без этой проверки
        // деструктуризация роняла приложение на старте — мимо задуманного «нет токена — не беда».
        ?.takeIf { it.size == 2 }
        ?.let { (access, refresh) -> BearerTokens(access, refresh) }

    override fun save(bearerTokens: BearerTokens) {
        file.parent?.createDirectories()
        file.writeText("${bearerTokens.accessToken}$SEPARATOR${bearerTokens.refreshToken}")
        restrictToOwner(file)
    }

    private companion object {
        const val SEPARATOR = "\n"

        /**
         * Права 600 сразу после записи.
         *
         * Не на создании файла: перезапись существующего оставила бы прежние права, а поправить
         * их надо и в этом случае. На файловой системе без POSIX-прав (Windows) молча ничего не
         * делаем — там разграничение даёт сам каталог профиля.
         */
        fun restrictToOwner(path: Path) {
            if ("posix" !in path.fileSystem.supportedFileAttributeViews()) return

            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}

/**
 * Каталог настроек по правилам платформы: `Application Support` на macOS, `%APPDATA%` на Windows,
 * `XDG_CONFIG_HOME` (или `~/.config`) на остальных.
 */
internal fun defaultSessionFile(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").orEmpty().lowercase()

    val directory = when {
        os.contains("mac") -> home / "Library" / "Application Support" / "mani"
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.pathString) / "mani"
        else -> Path.of(System.getenv("XDG_CONFIG_HOME") ?: (home / ".config").pathString) / "mani"
    }

    return directory / "session"
}
