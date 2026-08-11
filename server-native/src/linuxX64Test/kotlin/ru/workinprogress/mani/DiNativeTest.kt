package ru.workinprogress.mani

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.check.checkModules
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Граф нативной сборки собирается целиком.
 *
 * Модули у сборок разные — хранилище своё у каждой, — поэтому JVM-овый `ServerKoinModuleTest`
 * этот граф не покрывает. Забытая привязка порта здесь означала бы отказ на первом запросе,
 * а не на старте.
 */
class DiNativeTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    // `checkModules` объявлен устаревшим в пользу `verify()`, но тот держится на
    // рефлексии и существует только на JVM. Здесь замены нет.
    @Suppress("DEPRECATION")
    @Test
    fun checkKoinModule() {
        val config = TestMongo.config()
        startKoin {
            modules(coreModule(config), mongknStorageModule(config.mongo))
        }.checkModules()
    }
}
