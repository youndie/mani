package io.github.youndie.mani

import io.github.youndie.kore.health.ReadinessGate
import io.github.youndie.kore.ktor.EngineDrain
import io.github.youndie.kore.ktor.installShutdownRefusal
import io.github.youndie.kore.lifecycle.AnnounceNotReady
import io.github.youndie.kore.lifecycle.ShutdownDeadlines
import io.github.youndie.kore.lifecycle.ShutdownParticipant
import io.github.youndie.kore.lifecycle.ShutdownPlanBuilder
import io.github.youndie.kore.lifecycle.runUntilSignal
import io.github.youndie.mani.config.ManiConfig
import io.github.youndie.mani.feature.health.ShuttingDown
import io.github.youndie.mani.security.TokenService
import io.github.youndie.mani.web.WebAssets
import io.github.youndie.mani.web.webRoutes
import io.github.youndie.mongkn.MongoClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import kotlin.time.Duration.Companion.seconds

/**
 * Сколько оркестратор ждёт после SIGTERM, прежде чем послать SIGKILL.
 *
 * Названо здесь и там: `.k8s-templates/deployment.yaml` объявляет то же число
 * `terminationGracePeriodSeconds`. kore проверяет, что сумма его сроков в него укладывается, —
 * но проверить может только против того числа, которое ему сказали. Не сказать значит дать ему
 * проверять против предположения.
 */
private val GRACE_PERIOD = 30.seconds

/**
 * Запас на жёсткое закрытие соединений поверх срока слива.
 *
 * `EngineDrain` требует, чтобы срок был строго больше мягкого: разница между ними и есть окно,
 * в котором движок дорывает то, что само не закончилось.
 */
private val DRAIN_HARD_KILL = 5.seconds

fun main() {
    val config = ManiConfig.fromEnv()
    val deadlines = ShutdownDeadlines(gracePeriod = GRACE_PERIOD)

    /*
     * Готовность держится здесь, снаружи приложения, и это не мелочь размещения.
     *
     * Внутри `Application` она прожила бы ровно до `stop()`, а спросить её надо КАК РАЗ во время
     * остановки — и раньше, чем остановка началась, чтобы оркестратор успел убрать под из
     * endpoints.
     */
    val readiness = ReadinessGate()

    println("mani: старт на порту ${config.port}, mongo ${config.mongo.host}")
    print(deadlines.describe())

    val server =
        embeddedServer(CIO, port = config.port) {
            maniModule(config, ShuttingDown { readiness.isShuttingDown })

            /*
             * Пока идёт слив: то, что уже выполняется, доделывается; новым отвечаем 503 и
             * `Connection: close`. Пробы из этого исключены умолчанием kore — их набор путей
             * совпадает с тем, что этот сервер и отдаёт (`/health`, `/health/ready`), то есть
             * готовность продолжает отвечать «не готов», а не «выключаемся».
             */
            installShutdownRefusal(isShuttingDown = { readiness.isShuttingDown })
        }

    /*
     * `wait = false`, а не `true`.
     *
     * При `wait = true` главный поток остаётся внутри движка и до ожидания сигнала не доходит
     * никогда — то есть весь порядок ниже не выполняется ни разу.
     */
    server.start(wait = false)

    /*
     * Ссылка на пул берётся СЕЙЧАС, пока приложение живо.
     *
     * Взять её внутри плана было бы поздно: план строится после сигнала, а к тому моменту слив
     * уже закроет приложение вместе с графом Koin, и доставать из него будет нечего.
     *
     * Здесь же ответ на вопрос, ради которого это всё и затевалось: **до этой правки пул не
     * закрывал никто**. Ни `ApplicationStopping`, ни `onClose` у определения Koin — процесс
     * просто умирал по SIGTERM вместе с незавершёнными вызовами в C-драйвер. То, что порядок
     * шагов `EmbeddedServer.stop` на Kotlin/Native обратный по сравнению с JVM, эту сборку
     * поэтому не задевало: не тем, что порядок верный, а тем, что упорядочивать было нечего.
     */
    val mongo = server.application.get<MongoClient>()

    runBlocking {
        runUntilSignal(
            deadlines,
            // Внутри, а не после: возврат из этого вызова означает, что процесс уже уходит.
            onFinished = { run -> println("mani: остановка по ${run.signal}\n${run.transcript}") },
        ) {
            maniShutdown(
                readiness = readiness,
                engine = EngineDrain(server, deadlines.drain, deadlines.drain + DRAIN_HARD_KILL),
                mongo = mongoPool(mongo),
            )
        }
    }
}

/**
 * Порядок остановки этой сборки.
 *
 * Вынесен из [main] и принимает участников, а не движок с клиентом, чтобы проверяться тестом:
 * порядок — это и есть то, что здесь утверждается, а поднимать ради него настоящий сервер с
 * настоящей базой значит проверять их, а не его.
 */
internal fun ShutdownPlanBuilder.maniShutdown(
    readiness: ReadinessGate,
    engine: ShutdownParticipant,
    mongo: ShutdownParticipant,
) {
    // Готовность гаснет первой и с выдержкой: пока оркестратор не убрал под из endpoints,
    // новые запросы продолжают приходить, и слив сливал бы в непустой кран.
    announce(AnnounceNotReady(readiness))

    // Потом слив: приём прекращается, начатое доделывается.
    drain(engine)

    // И только потом пул. Наоборот — значит закрыть C-клиент под вызовами, которые ещё идут
    // внутри него: у нативного клиента это не «оборванный ответ», а работа в чужой памяти.
    pool(mongo)
}

private fun mongoPool(client: MongoClient): ShutdownParticipant = object : ShutdownParticipant {
    override val name: String = "mongo pool"

    override suspend fun stop() {
        client.close()
    }
}

/**
 * Сборка приложения. Вынесена из [main] отдельной функцией, чтобы её можно было поднять в тесте
 * через `testApplication` — с той же проводкой, что в бою.
 */
fun Application.maniModule(
    config: ManiConfig,
    /*
     * Умолчание — «остановки не бывает», и оно для тестов: `testApplication` поднимает это
     * приложение без процесса, которому можно послать сигнал.
     */
    shuttingDown: ShuttingDown = ShuttingDown.NEVER,
) {
    configureManiPlugins(config)

    install(Koin) {
        // Логгер Koin здесь по умолчанию: `koin-logger-slf4j` — JVM-only.
        modules(coreModule(config, shuttingDown), mongknStorageModule(config.mongo))
    }

    configureManiAuth(config, get<TokenService>())

    // Каталог статики читается **один раз на старте**, а не на каждый запрос: файлы вшиты
    // в образ и за время жизни процесса не меняются. Пустой `MANI_WEB_ROOT` — сервер без
    // фронтенда; так удобно поднимать его в тестах и локально.
    val assets = config.webRoot?.let(WebAssets::scan)
    if (assets != null) {
        println("mani: статика из ${config.webRoot}, файлов ${assets.size}")
    }

    routing {
        maniApiRouting()
        if (assets != null) webRoutes(assets)
    }
}
