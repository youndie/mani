package io.github.youndie.mani.config

actual fun readEnv(name: String): String? = System.getenv(name)
