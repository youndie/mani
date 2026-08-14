package ru.workinprogress.mani.data

/**
 * @param status код ответа, если он был. `null` значит, что ответа не было вовсе — это разные
 *  беды: «сервер сказал 503» и «до сервера не дошло», и человеку на экране их стоит различать.
 */
open class ServerException(
    override val message: String = "Server error",
    override val cause: Exception? = null,
    val status: Int? = null,
) : Exception(message, cause)
