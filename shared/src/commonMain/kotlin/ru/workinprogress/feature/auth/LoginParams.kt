package ru.workinprogress.feature.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginParams(val name: String, val password: String)
