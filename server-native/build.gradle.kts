import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.pluginSerialization)
    id("io.github.youndie.sborka.base")
    id("io.github.youndie.sborka.lint")
}

/*
 * Нативная сборка сервера — образ стенда.
 *
 * Таргет один и только `linuxX64`: mongkn публикуется под него и больше ни подо что (обвязке над
 * C-драйвером нужны заголовки целевой платформы), так что ни macosArm64, ни linuxArm64 здесь
 * появиться не могут. Практическое следствие: **собирается и проверяется этот модуль только на
 * Linux** — с macOS его не слинковать.
 */
kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "io.github.youndie.mani.main"
                baseName = "mani"

                // Резидентная память идёт за числом потоков, а не за живой кучей.
                //
                // Аллокатор Kotlin/Native держит страницу в 256 КиБ на каждый класс размеров
                // блока И НА КАЖДЫЙ ПОТОК; поток владеет ею, пока жив, а `Dispatchers.IO`
                // заводит потоки под нагрузкой. Настройки GC это не ограничивают — считать
                // нечего, это страницы, а не объекты.
                //
                // Замерено ЗДЕСЬ, а не взято по аналогии с katcher: релизный бинарь, 50
                // одновременных клиентов на `/health/ready` (каждый запрос — ping в Mongo),
                // два прогона на вариант.
                //
                //   по умолчанию          33 МБ в покое, пик 491–495 МБ
                //   fixedBlockPageSize=16 19 МБ в покое, пик 109–141 МБ
                //
                // Замер попутно опроверг посылку задачи (#170): предел, которого «здесь нет»,
                // здесь есть — `.k8s-templates/deployment.yaml`. Со 128Mi, которые там стояли,
                // под убивало бы и с этой опцией тоже, поэтому предел поднят до 256Mi; число
                // в чарте объяснено там же.
                //
                // `-Xallocator=std` НЕ брать по аналогии: на том же стенде с базой на пути
                // запроса его пик вышел ВЫШЕ, а не ниже.
                binaryOption("fixedBlockPageSize", "16")
            }

            // Тесты гоняются **и в релизе**, а не только в отладке, и это не перестраховка.
            // Kotlin/Native в релизе не вставляет проверок приведения типов, поэтому код,
            // который в отладке честно падает `ClassCastException` (и чей-то `catch` его
            // ловит), в релизе уходит в неопределённое поведение. Так `/auth/refresh` отдавал
            // 500 на стенде при полностью зелёном прогоне: тестовый бинарь собирался
            // отладочным, а в образ едет релизный.
            test(listOf(NativeBuildType.RELEASE))
        }

        // Один только релизный бинарь задачу запуска не создаёт: она заводится под тестовый
        // прогон, а он по умолчанию единственный и привязан к отладочному. Отсюда
        // `:server-native:linuxX64ReleaseTest`.
        testRuns.create("release") {
            setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE))
        }
    }

    sourceSets {
        val linuxX64Main by getting {
            dependencies {
                implementation(projects.serverCommon)
                implementation(projects.shared)

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.resources)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.koin.core)
                implementation(libs.koin.ktor)

                implementation(libs.mongkn.core)
                implementation(libs.mongkn.extensions)

                // Остановка по порядку. Нужна именно здесь: `EmbeddedServer.stop` выполняет
                // свои шаги в ПРОТИВОПОЛОЖНОМ порядке на Kotlin/Native и на JVM, то есть
                // платформа, на которой этот модуль разворачивается, — не та, на которой
                // общий код проверяется.
                implementation(libs.kore.core)
                implementation(libs.kore.ktor)

                // `runBlocking` в точке входа: главный поток обязан дойти до ожидания сигнала,
                // а не остаться внутри `start(wait = true)`. Объявлено явно, хотя приезжает и
                // транзитивно через ktor — см. соседний комментарий про провайдер криптографии.
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                // Отдача статики: `staticFiles`/`staticResources` под Kotlin/Native недоступны,
                // файлы читаются вручную.
                implementation(libs.kotlinx.io.core)

                // Провайдер криптографии объявлен здесь **повторно**, хотя приезжает и
                // транзитивно из `:server-common`. Полагаться на чужой `implementation` значит
                // потерять его молча в тот день, когда та зависимость переедет, — а без
                // провайдера падает не сборка, а первый логин.
                implementation(libs.cryptography.provider.openssl3)
            }
        }

        val linuxX64Test by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.server.tests)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.koin.test)
            }
        }
    }
}
