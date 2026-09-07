package io.github.youndie.mani.feature.auth.domain

import io.github.youndie.mani.feature.auth.LoginParams
import io.github.youndie.mani.useCase.UseCase

abstract class AuthUseCase : UseCase<LoginParams, Boolean>()
