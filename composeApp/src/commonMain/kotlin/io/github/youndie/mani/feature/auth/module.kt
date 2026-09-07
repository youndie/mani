package io.github.youndie.mani.feature.auth

import io.github.youndie.mani.feature.auth.data.TokenRepository
import io.github.youndie.mani.feature.auth.data.TokenRepositoryCommon
import io.github.youndie.mani.feature.auth.data.TokenStorage
import io.github.youndie.mani.feature.auth.data.TokenStorageCommon
import io.github.youndie.mani.feature.auth.domain.DemoUseCase
import io.github.youndie.mani.feature.auth.domain.LogoutUseCase
import io.github.youndie.mani.feature.auth.domain.StartDemoUseCase
import io.github.youndie.mani.feature.demo.domain.SeedDemoDataUseCase
import io.github.youndie.mani.feature.demo.domain.SeedUseCase
import io.github.youndie.mani.feature.health.domain.GetHealthUseCase
import io.github.youndie.mani.feature.health.domain.HealthUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val authModulePlatform: Module

val authModule = module {
    single<TokenStorage> { TokenStorageCommon() }
    single<TokenRepository> { TokenRepositoryCommon(get()) }
    includes(authModulePlatform)

    singleOf(::LogoutUseCase)

    // Здесь, а не в модуле экрана входа: `AuthViewModel` общая у входа и регистрации,
    // и незарегистрированная зависимость уронила бы оба экрана, а не один.
    singleOf(::StartDemoUseCase).bind<DemoUseCase>()

    // Засев своего аккаунта нужен пустому экрану, а он живёт вне экрана входа.
    singleOf(::SeedDemoDataUseCase).bind<SeedUseCase>()

    singleOf(::GetHealthUseCase).bind<HealthUseCase>()
}
