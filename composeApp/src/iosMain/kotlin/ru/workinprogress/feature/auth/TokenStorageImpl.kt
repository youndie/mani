package ru.workinprogress.feature.auth

import io.ktor.client.plugins.auth.providers.*
import platform.Foundation.NSUserDefaults
import ru.workinprogress.feature.auth.data.TokenStorage

class TokenStorageImpl : TokenStorage {
    override fun load() = BearerTokens(
        NSUserDefaults.standardUserDefaults.stringForKey(BearerTokens::accessToken.name).orEmpty(),
        NSUserDefaults.standardUserDefaults.stringForKey(BearerTokens::refreshToken.name).orEmpty(),
    )

    override fun save(bearerTokens: BearerTokens) {
        NSUserDefaults.standardUserDefaults.setObject(bearerTokens.accessToken, BearerTokens::accessToken.name)
        NSUserDefaults.standardUserDefaults.setObject(bearerTokens.refreshToken, BearerTokens::refreshToken.name)
    }
}
