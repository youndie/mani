package io.github.youndie.mani.feature.auth

import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.ktor.client.plugins.auth.providers.*
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set

class TokenStorageImpl : TokenStorage {
    override fun load() = BearerTokens(
        window.localStorage[BearerTokens::accessToken.name].orEmpty(),
        window.localStorage[BearerTokens::refreshToken.name].orEmpty(),
    )

    override fun save(bearerTokens: BearerTokens) {
        window.localStorage[BearerTokens::accessToken.name] = bearerTokens.accessToken
        window.localStorage[BearerTokens::refreshToken.name] = bearerTokens.refreshToken.orEmpty()
    }
}
