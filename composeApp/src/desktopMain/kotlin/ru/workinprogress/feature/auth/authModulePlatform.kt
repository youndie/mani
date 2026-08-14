package ru.workinprogress.feature.auth

import org.koin.core.module.Module
import org.koin.dsl.module
import ru.workinprogress.feature.auth.data.TokenStorage
import ru.workinprogress.feature.auth.data.TokenStorageImpl

actual val authModulePlatform: Module = module {
    single<TokenStorage> { TokenStorageImpl() }
}
