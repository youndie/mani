package ru.workinprogress.feature.auth.domain

import ru.workinprogress.feature.auth.LoginParams
import ru.workinprogress.useCase.UseCase

abstract class AuthUseCase : UseCase<LoginParams, Boolean>()
