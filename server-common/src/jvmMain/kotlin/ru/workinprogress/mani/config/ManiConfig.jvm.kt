package ru.workinprogress.mani.config

actual fun readEnv(name: String): String? = System.getenv(name)
