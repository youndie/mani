plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.pluginSerialization)
}

/**
 * Код сервера: маршруты, порты хранилища, конфигурация, аутентификация.
 *
 * Модуль **не содержит `main` и не собирает образ** — этим заняты `:server` (JVM) и
 * `:server-native`. Причина не в эстетике: Ktor Gradle plugin, jib и `application` работают
 * только с `kotlinJvm` и на KMP-проект не встают, а нативной сборке нужен свой Dockerfile.
 *
 * Платформенного здесь ровно одно — чтение переменных окружения. Всё остальное, включая подпись
 * токенов и хеширование паролей, общее: обе сборки обязаны считать подпись и хеш **одним кодом**,
 * иначе токен, выданный одной, не примет другая, а разойдутся они молча.
 *
 * Реализации хранилища сюда не входят: официальный драйвер существует только на JVM, mongkn —
 * только под linuxX64. В общей части живут интерфейсы, реализации — в сборочных модулях.
 */
kotlin {
    jvm()
    jvmToolchain(21)

    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared)

            api(libs.ktor.server.core)
            api(libs.ktor.server.auth)
            implementation(libs.ktor.server.resources)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.cors)

            api(libs.koin.core)
            implementation(libs.koin.ktor)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.cryptography.core)
            implementation(libs.cryptography.random)
        }

        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }

        // Провайдер криптографии обязан быть объявлен в **каждой** сборке: без него
        // `CryptographyProvider.Default` падает при первом обращении, то есть на первом же
        // логине, а не на сборке.
        val linuxX64Main by getting {
            dependencies {
                implementation(libs.cryptography.provider.openssl3)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            // Эталон, а не зависимость продукта: токены до перехода выдавал `com.auth0:java-jwt`,
            // и его подписью проверяется, что новый верификатор принимает старые refresh-токены,
            // уже лежащие в базе.
            implementation(libs.auth0.java.jwt)
        }
    }
}
