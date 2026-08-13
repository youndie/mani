package ru.workinprogress.feature.demo.domain

import ru.workinprogress.useCase.NonParameterizedUseCase

/**
 * Засев своего аккаунта данными сида. Абстракция затем же, зачем
 * [ru.workinprogress.feature.auth.domain.DemoUseCase]: за ней стоит сеть, и без неё виртуальная
 * машина главного экрана не собирается в тесте.
 */
abstract class SeedUseCase : NonParameterizedUseCase<Boolean>()
