package ru.workinprogress.feature.auth.data

import io.ktor.client.plugins.auth.providers.BearerTokens

class TokenStorageCommon : TokenStorage {
    override fun load(): BearerTokens? = null
    override fun save(bearerTokens: BearerTokens) {}
}
