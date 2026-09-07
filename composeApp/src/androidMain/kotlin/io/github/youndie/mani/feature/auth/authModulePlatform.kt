package io.github.youndie.mani.feature.auth

import android.content.Context
import androidx.preference.PreferenceManager
import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.github.youndie.mani.feature.auth.data.TokenStorageImpl
import org.koin.core.module.Module
import org.koin.dsl.module

actual val authModulePlatform: Module = module {
    single<TokenStorage> {
        TokenStorageImpl(PreferenceManager.getDefaultSharedPreferences(get<Context>()))
    }
}
