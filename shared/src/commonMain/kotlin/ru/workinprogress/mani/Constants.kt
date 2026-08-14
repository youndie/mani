package ru.workinprogress.mani

data class ServerConfig(
    val name: String,
    val scheme: String,
    val host: String,
    val development: Boolean = false,
    val port: String? = null,
)

val staging = ServerConfig(
    "Staging",
    "https",
    "mani.kotlin.website",
)

val local = ServerConfig(
    "Local",
    scheme = "http",
    host = "192.168.1.230",
    development = true,
    port = "8080",
)

val currentServerConfig: ServerConfig = staging
