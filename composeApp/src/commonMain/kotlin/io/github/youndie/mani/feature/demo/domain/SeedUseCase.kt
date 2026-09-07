package io.github.youndie.mani.feature.demo.domain

import io.github.youndie.mani.useCase.NonParameterizedUseCase

/**
 * Засев своего аккаунта данными сида. Абстракция затем же, зачем
 * [io.github.youndie.mani.feature.auth.domain.DemoUseCase]: за ней стоит сеть, и без неё виртуальная
 * машина главного экрана не собирается в тесте.
 */
abstract class SeedUseCase : NonParameterizedUseCase<Boolean>()
