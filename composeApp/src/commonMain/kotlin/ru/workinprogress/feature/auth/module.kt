package ru.workinprogress.feature.auth

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.auth.data.TokenRepository
import ru.workinprogress.feature.auth.data.TokenRepositoryCommon
import ru.workinprogress.feature.auth.data.TokenStorage
import ru.workinprogress.feature.auth.data.TokenStorageCommon
import ru.workinprogress.feature.auth.domain.LogoutUseCase
import ru.workinprogress.feature.auth.domain.DemoUseCase
import ru.workinprogress.feature.auth.domain.StartDemoUseCase

expect val authModulePlatform: Module

val authModule = module {
    single<TokenStorage> { TokenStorageCommon() }
    single<TokenRepository> { TokenRepositoryCommon(get()) }
    includes(authModulePlatform)

    singleOf(::LogoutUseCase)

    // Здесь, а не в модуле экрана входа: `AuthViewModel` общая у входа и регистрации,
    // и незарегистрированная зависимость уронила бы оба экрана, а не один.
    singleOf(::StartDemoUseCase).bind<DemoUseCase>()
}

