package io.github.youndie.mani.feature.auth

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.ktor.client.plugins.auth.providers.BearerTokens

/**
 * Токены сессии в связке ключей.
 *
 * Раньше это был `NSUserDefaults`. Другое приложение оттуда не прочитает, но `NSUserDefaults`
 * целиком уезжает в резервную копию — в iCloud и в копию на компьютере, — и токен доступа,
 * дающий те же права, что пароль, лежит там открытым текстом. Связка ключей для этого и есть.
 *
 * Берётся из `multiplatform-settings`, который в проекте уже есть: своя обвязка над Security
 * означала бы десятки строк работы с `CFDictionary` ради того, что уже написано и проверено.
 *
 * Токены, оставшиеся в `NSUserDefaults` от прежних версий, не переносятся: их всего один вход,
 * и человек просто войдёт заново.
 */
@OptIn(ExperimentalSettingsImplementation::class)
class TokenStorageImpl(private val settings: Settings = KeychainSettings(SERVICE)) : TokenStorage {

    override fun load(): BearerTokens? {
        val access = settings.getStringOrNull(ACCESS) ?: return null
        val refresh = settings.getStringOrNull(REFRESH) ?: return null

        return BearerTokens(access, refresh)
    }

    override fun save(bearerTokens: BearerTokens) {
        settings.putString(ACCESS, bearerTokens.accessToken)
        settings.putString(REFRESH, bearerTokens.refreshToken.orEmpty())
    }

    private companion object {
        const val SERVICE = "io.github.youndie.mani.tokens"
        const val ACCESS = "accessToken"
        const val REFRESH = "refreshToken"
    }
}
