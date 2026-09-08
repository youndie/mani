package io.github.youndie.mani.feature.auth.data

import io.ktor.client.plugins.auth.providers.BearerTokens
import java.io.File

class TokenStorageImpl : TokenStorage {

    private val file by lazy {
        File(FILENAME).apply {
            if (!exists()) {
                createNewFile()
            }
        }
    }

    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "нет файла с токеном — обычное состояние первого запуска, а не отказ",
    )
    override fun load(): BearerTokens? = try {
        file.readText().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }?.split(SEPARATOR)
        // Оборванная запись оставляет в файле одну строку вместо двух. Без этой проверки
        // деструктуризация роняла приложение на старте — мимо задуманного «нет токена — не беда».
        ?.takeIf { it.size == 2 }
        ?.let { (access, refresh) -> BearerTokens(access, refresh) }

    override fun save(bearerTokens: BearerTokens) {
        file.writeText("${bearerTokens.accessToken}$SEPARATOR${bearerTokens.refreshToken}")
    }

    companion object {
        private const val FILENAME = "session.txt"
        private const val SEPARATOR = "\n"
    }
}
