package io.github.youndie.mani

import io.github.youndie.mani.feature.health.ShuttingDown
import io.github.youndie.mani.feature.health.StorageHealth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Готовность отвечает за базу, а живость — за процесс.
 *
 * До этого у развёртывания не было проб вовсе: k8s считал готовым любой запустившийся процесс,
 * и выкат с недоступной базой доезжал до конца зелёным. Проба на `/health` этого бы не поймала —
 * он отвечает, ничего не спрашивая у хранилища, и в этом его смысл.
 */
class HealthReadinessTest {

    @Test
    fun `readiness answers 200 while the database answers`() = maniTest {
        assertEquals(HttpStatusCode.OK, createClient { }.get("/health/ready").status)
    }

    /**
     * Хранилище молчит — под не готов.
     *
     * Проверяется МАРШРУТ: что «нет» от хранилища становится 503, а не 200 и не 500.
     *
     * Чего этот тест не проверяет — что реализация действительно ходит в базу: подменена как раз
     * она. Замени `runCommand` на `true`, и оба случая останутся зелёными, а проба зеленела бы
     * при мёртвой базе. Поймать это тестом дорого: у драйверов таймаут выбора сервера — тридцать
     * секунд, и прогон против несуществующего адреса стоил бы полминуты на случай. Поэтому
     * реализации оставлены в три строки, где видно, что вызывается.
     */
    @Test
    fun `readiness answers 503 when the storage does not`() {
        val brokenStorage = module { single<StorageHealth> { StorageHealth { false } } }

        maniTest(overrides = listOf(brokenStorage)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, createClient { }.get("/health/ready").status)
        }
    }

    /**
     * Идёт остановка — под не готов, хотя база отвечает.
     *
     * Это и есть первый шаг упорядоченной остановки: готовность гаснет ДО слива, чтобы
     * оркестратор успел убрать под из endpoints. Без этого ответа шаг был бы не наблюдаем —
     * `announce` переключал бы флаг, которого никто не спрашивает.
     */
    @Test
    fun `readiness answers 503 while the process is shutting down`() {
        val shuttingDown = module { single<ShuttingDown> { ShuttingDown { true } } }

        maniTest(overrides = listOf(shuttingDown)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, createClient { }.get("/health/ready").status)
        }
    }

    /**
     * И хранилище при этом не спрашивается.
     *
     * Порядок двух проверок внутри маршрута — не вкусовщина: во время слива база жива и отвечает
     * «готов», так что вопрос к ней перекрыл бы ответ про остановку. Проверяется счётчиком, а не
     * ответом: оба порядка дают один и тот же 503, и тест, смотрящий на код, прошёл бы при
     * перестановке строк.
     */
    @Test
    fun `readiness does not ask the storage while shutting down`() {
        val asked = AtomicInteger()
        val overrides = module {
            single<ShuttingDown> { ShuttingDown { true } }
            single<StorageHealth> {
                StorageHealth {
                    asked.incrementAndGet()
                    true
                }
            }
        }

        maniTest(overrides = listOf(overrides)) {
            createClient { }.get("/health/ready")
        }

        assertEquals(0, asked.get(), "проба готовности сходила в базу, хотя процесс уже останавливается")
    }

    /** Живость не зависит от базы: иначе её падение перезапускало бы поды вместо снятия трафика. */
    @Test
    fun `liveness answers while the storage does not`() {
        val brokenStorage = module { single<StorageHealth> { StorageHealth { throw IllegalStateException("no db") } } }

        maniTest(overrides = listOf(brokenStorage)) {
            assertEquals(HttpStatusCode.OK, createClient { }.get("/health").status)
        }
    }
}
