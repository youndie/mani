package io.github.youndie.mani.feature.auth

import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.github.youndie.mani.feature.auth.data.TokenStorageImpl
import org.koin.core.module.Module
import org.koin.dsl.module

actual val authModulePlatform: Module = module {
    single<TokenStorage> { TokenStorageImpl() }
}
