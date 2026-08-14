package ru.workinprogress.feature.auth.data

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenRepositoryCommon(private val storage: TokenStorage) : TokenRepository {
    private val token: MutableStateFlow<BearerTokens> = MutableStateFlow(
        storage.load() ?: BearerTokens("", ""),
    )

    override fun getToken(): BearerTokens = token.value

    override fun set(accessToken: String, refreshToken: String) {
        this.token.value = BearerTokens(accessToken, refreshToken)

        storage.save(this.token.value)
    }

    override fun observeToken(): StateFlow<BearerTokens> = token.asStateFlow()
}
