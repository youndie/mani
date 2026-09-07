package io.github.youndie.mani.feature.auth.data

import io.ktor.client.plugins.auth.providers.BearerTokens

interface TokenStorage {
    fun load(): BearerTokens?
    fun save(bearerTokens: BearerTokens)
}
