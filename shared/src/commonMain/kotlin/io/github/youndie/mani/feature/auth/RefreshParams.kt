package io.github.youndie.mani.feature.auth

import kotlinx.serialization.Serializable

@Serializable
data class RefreshParams(val refreshToken: String)
