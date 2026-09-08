package io.github.youndie.mani

/*
 * Куда клиент ходит за API, если платформа не сказала иного.
 *
 * Живёт у клиента, а не в контракте: серверу адрес самого себя не нужен, а `:shared` называется
 * контрактом и должен им быть. Отсюда же убран `local` — конфигурация с адресом домашней сети
 * одного человека, которую больше не заменяет собой `MANI_SERVER` и `platformServerConfig()`.
 */

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

val currentServerConfig: ServerConfig = staging
