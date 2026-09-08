package io.github.youndie.mani

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Одна запись — один владелец, проверено на JVM-сборке.
 *
 * Те же случаи проверяет `ManiApiTest` в `:server-native`, и это не дублирование: маршрут общий,
 * а фильтр записи свой у каждой реализации хранилища. Дыра жила именно в паре «проверка по пути,
 * запись по телу», то есть ровно на стыке общего кода с реализацией, и одной проверки на одну
 * сборку недостаточно — вторая реализация может починиться, а первая остаться.
 *
 * Транзакции и категории лежат здесь вместе, потому что это одна и та же ошибка в двух местах:
 * проверка смотрит на путь, запись слушает тело.
 */
class OwnershipTest {

    /**
     * Идентификатор правится по пути, а не по телу.
     *
     * Проверка владельца смотрела на `path.id`, а документ на запись выбирался по `id` из тела:
     * достаточно было отправить `PATCH` на СВОЮ запись, приложив в теле чужую, — и чужая
     * переписывалась, заодно меняя владельца на вызывающего. Ответ при этом был 200, то есть
     * снаружи всё выглядело исправным.
     */
    @Test
    fun `a stranger cannot patch a foreign transaction through the id in the body`() = maniTest {
        val client = createClient { }

        val owner = client.signIn("owner", "hunter22")
        val stranger = client.signIn("stranger", "hunter22")

        val theirs = client.createTransaction(owner, "theirs")
        val mine = client.createTransaction(stranger, "mine")

        // В лоб: чужой идентификатор в пути. Это было закрыто и раньше.
        val direct = client.patchTransaction(stranger, path = theirs.id, body = theirs.copy(comment = "stolen"))
        assertEquals(HttpStatusCode.Forbidden, direct.status)

        // Обходом: свой идентификатор в пути, чужой — в теле.
        val smuggled = client.patchTransaction(stranger, path = mine.id, body = theirs.copy(comment = "stolen"))
        assertEquals(HttpStatusCode.OK, smuggled.status)

        val ownersNow = client.transactions(owner)
        assertEquals(1, ownersNow.size, "чужая запись сменила владельца")
        assertEquals("theirs", ownersNow.single().comment, "чужая запись переписана")

        // А своя запись правится: путь и решает, что именно пишется.
        assertEquals(listOf("stolen"), client.transactions(stranger).map { it.comment })
    }

    /**
     * То же самое у категорий, и потому отдельным случаем: маршрут другой, хранилище другое,
     * а ошибка одна — принадлежность проверялась по пути, переименовывалось названное телом.
     */
    @Test
    fun `a stranger cannot rename a foreign category through the id in the body`() = maniTest {
        val client = createClient { }

        val owner = client.signIn("owner", "hunter22")
        val stranger = client.signIn("stranger", "hunter22")

        val theirs = client.createCategory(owner, "Food")
        val mine = client.createCategory(stranger, "Mine")

        val direct = client.patchCategory(stranger, path = theirs.id, body = theirs.copy(name = "stolen"))
        assertEquals(HttpStatusCode.Forbidden, direct.status)

        val smuggled = client.patchCategory(stranger, path = mine.id, body = theirs.copy(name = "stolen"))
        assertEquals(HttpStatusCode.OK, smuggled.status)

        assertEquals(listOf("Food"), client.categories(owner).map { it.name }, "чужая категория переименована")
        assertEquals(listOf("stolen"), client.categories(stranger).map { it.name })
    }
}
