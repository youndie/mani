package ru.workinprogress.feature.auth.domain

import ru.workinprogress.useCase.NonParameterizedUseCase

/**
 * Вход в песочницу. Абстракция ровно затем же, зачем [AuthUseCase]: за ней стоит сеть, и без
 * неё виртуальная машина экрана входа не проверяется тестом.
 */
abstract class DemoUseCase : NonParameterizedUseCase<Boolean>()
