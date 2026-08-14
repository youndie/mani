package ru.workinprogress.feature.auth.data

import android.content.SharedPreferences
import io.ktor.client.plugins.auth.providers.*

class TokenStorageImpl(private val prefs: SharedPreferences) : TokenStorage {
    override fun load(): BearerTokens? = BearerTokens(
        prefs.getString(BearerTokens::accessToken.name, "").orEmpty(),
        prefs.getString(BearerTokens::refreshToken.name, "").orEmpty(),
    )

    override fun save(bearerTokens: BearerTokens) {
        prefs.edit()
            .putString(BearerTokens::accessToken.name, bearerTokens.accessToken)
            .putString(BearerTokens::refreshToken.name, bearerTokens.refreshToken)
            .commit()
    }
}
