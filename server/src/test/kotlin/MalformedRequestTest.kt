package io.github.youndie.mani

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Испорченный ввод — это 400, и на JVM-сборке тоже.
 *
 * Раскладка исключения в ответ общая (`configureManiPlugins`), а вот САМО исключение у двух
 * реализаций хранилища разное: `not-an-id` здесь отвергает конструктор `ObjectId`, на нативной
 * сборке — сериализатор поля. Поэтому случай проверяется в каждой сборке своим тестом; парный
 * живёт в `ManiApiTest` в `:server-native`.
 */
class MalformedRequestTest {

    @Test
    fun `a malformed id in the path is a bad request`() = maniTest {
        val client = createClient { }
        val token = client.signIn("malformed", "hunter22")

        assertEquals(HttpStatusCode.BadRequest, client.deleteTransaction(token, "not-an-id").status)
    }

    /**
     * Тело, которое не разбирается, — тоже 400, и проверка эта не холостая.
     *
     * Ktor отвечал так и до `StatusPages`: `BadRequestException` он раскладывает сам. Но
     * обработчик выше ловит `Throwable`, то есть перехватывает и его тоже, — и стоит убрать
     * из `when` ветку `BadRequestException`, как ответ становится 500. Сторожится именно это:
     * не поведение Ktor, а то, что общий обработчик его не испортил.
     */
    @Test
    fun `an unparseable body is a bad request`() = maniTest {
        val client = createClient { }
        val token = client.signIn("malformed", "hunter22")

        val response = client.post("/transactions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{\"amount\": ")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
