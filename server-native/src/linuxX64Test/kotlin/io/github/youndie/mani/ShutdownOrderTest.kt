package io.github.youndie.mani

import io.github.youndie.kore.health.ReadinessGate
import io.github.youndie.kore.lifecycle.KoreStage
import io.github.youndie.kore.lifecycle.ShutdownDeadlines
import io.github.youndie.kore.lifecycle.ShutdownParticipant
import io.github.youndie.kore.lifecycle.shutdownSequence
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Порядок остановки нативной сборки.
 *
 * Проверяется план, а не библиотека: что стадии идут по своему списку, kore утверждает у себя и
 * своими тестами. Здесь утверждается то, что принадлежит mani, — **что закрывать пул Mongo можно
 * только после того, как движок слил начатое**, и что готовность к этому моменту уже погашена.
 *
 * Почему это стоит теста, а не комментария: `EmbeddedServer.stop` выполняет свои шаги в
 * ПРОТИВОПОЛОЖНОМ порядке на Kotlin/Native и на JVM. Тот же исходник, обратный порядок, и ничто
 * об этом не сообщает — а разворачивается именно нативная сборка, тогда как большая часть тестов
 * общего кода идёт на JVM.
 *
 * Что этот тест НЕ ловит, сказано вслух, потому что выглядит он так, будто ловит: перестановка
 * самих вызовов внутри [maniShutdown] его не роняет. Строитель kore объявительный — участник
 * попадает в стадию по имени метода, а стадии идут своим списком, — так что поменять местами
 * `drain` и `pool` значит не изменить ничего. Проверено перестановкой: тест остался зелёным.
 * Роняет его то, что действительно меняет поведение, — пул, записанный в стадию ДО слива
 * (проверено: `pool` → `announce` даёт красный).
 *
 * Сроки взяты короткие: проверяется последовательность, а боевые пятнадцать секунд слива сделали
 * бы из неё ожидание.
 */
class ShutdownOrderTest {

    private val deadlines = ShutdownDeadlines(
        preDrainWait = 10.milliseconds,
        drain = 50.milliseconds,
        releaseGroup = 10.milliseconds,
        gracePeriod = 5.seconds,
    )

    @Test
    fun theMongoPoolClosesAfterTheEngineHasDrained() = runTest {
        val calls = mutableListOf<String>()
        val readiness = ReadinessGate()

        val transcript = shutdownSequence(deadlines) {
            maniShutdown(
                readiness = readiness,
                engine = recording("engine", calls),
                mongo = recording("mongo", calls),
            )
        }.run()

        assertEquals(
            listOf("engine", "mongo"),
            calls,
            "пул закрылся не после слива — вызовы, ещё идущие внутри C-драйвера, остались бы без него",
        )
        assertEquals(emptyList(), transcript.failures.map(Any::toString))
    }

    /**
     * Готовность гаснет раньше слива, а не вместе с ним.
     *
     * Утверждение снимается ИЗНУТРИ участника слива, а не после прогона: к концу всё равно
     * погашено, и проверка в конце прошла бы и в том случае, если `announce` стоял бы последним.
     */
    @Test
    fun readinessIsAlreadyDownWhenTheEngineStartsDraining() = runTest {
        val readiness = ReadinessGate()
        var readinessDuringDrain: Boolean? = null

        shutdownSequence(deadlines) {
            maniShutdown(
                readiness = readiness,
                engine = participant("engine") { readinessDuringDrain = readiness.isShuttingDown },
                mongo = participant("mongo") { },
            )
        }.run()

        assertEquals(
            true,
            readinessDuringDrain,
            "слив начался, пока под ещё числится готовым — оркестратор продолжал бы слать на него новые запросы",
        )
    }

    /** Полный список стадий печатается в транскрипте, по которому стенд и читает остановку. */
    @Test
    fun theTranscriptNamesEveryStage() = runTest {
        val readiness = ReadinessGate()

        val transcript = shutdownSequence(deadlines) {
            maniShutdown(readiness, participant("engine") { }, participant("mongo") { })
        }.run()

        assertEquals(KoreStage.specifiedOrder, transcript.order)
        assertTrue(readiness.isShuttingDown, "остановка прошла, а готовность осталась включённой")
    }

    private fun recording(name: String, into: MutableList<String>): ShutdownParticipant =
        participant(name) { into += name }

    private fun participant(participantName: String, onStop: suspend () -> Unit): ShutdownParticipant =
        object : ShutdownParticipant {
            override val name: String = participantName

            override suspend fun stop() = onStop()
        }
}
