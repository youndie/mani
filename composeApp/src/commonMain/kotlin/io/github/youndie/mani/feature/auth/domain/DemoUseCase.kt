package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.useCase.NonParameterizedUseCase

/**
 * Вход в песочницу. Абстракция ровно затем же, зачем [AuthUseCase]: за ней стоит сеть, и без
 * неё виртуальная машина экрана входа не проверяется тестом.
 */
abstract class DemoUseCase : NonParameterizedUseCase<Boolean>()
