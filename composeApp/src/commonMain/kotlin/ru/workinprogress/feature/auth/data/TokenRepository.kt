package ru.workinprogress.feature.auth.data

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.flow.StateFlow

interface TokenRepository {
    fun getToken(): BearerTokens
    fun set(accessToken: String = getToken().accessToken, refreshToken: String = getToken().refreshToken.orEmpty())

    fun observeToken(): StateFlow<BearerTokens>
}
