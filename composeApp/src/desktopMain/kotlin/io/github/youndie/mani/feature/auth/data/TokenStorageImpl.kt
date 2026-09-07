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
    }?.split(SEPARATOR)?.let { tokens ->
        val (access, refresh) = tokens
        BearerTokens(access, refresh)
    }

    override fun save(bearerTokens: BearerTokens) {
        file.writeText("${bearerTokens.accessToken}$SEPARATOR${bearerTokens.refreshToken}")
    }

    companion object {
        private const val FILENAME = "session.txt"
        private const val SEPARATOR = "\n"
    }
}
