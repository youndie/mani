package io.github.youndie.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.category.CategoryRepository
import io.github.youndie.mani.feature.category.data.MongoCategoryRepository
import io.github.youndie.mani.feature.health.StorageHealth
import io.github.youndie.mani.feature.transaction.data.MongoTransactionRepository
import io.github.youndie.mani.feature.transaction.data.TransactionRepository
import io.github.youndie.mani.feature.user.data.MongoTokenRepository
import io.github.youndie.mani.feature.user.data.MongoUserRepository
import io.github.youndie.mani.feature.user.data.TokenRepository
import io.github.youndie.mani.feature.user.data.UserRepository
import org.bson.Document
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Хранилище JVM-сборки: официальный драйвер MongoDB.
 *
 * Второй такой модуль есть в `:server-native` — на mongkn. Общими остаются только порты, и это
 * не дублирование, а единственный способ иметь две сборки: драйвер существует лишь на JVM,
 * mongkn — лишь под linuxX64.
 */
fun mongoStorageModule(mongoConfig: MongoConfig): Module = module {
    single<MongoClient> { MongoClient.create(mongoConfig.connectionString) }
    single<MongoDatabase> { get<MongoClient>().getDatabase(mongoConfig.database) }

    // Живость базы: `ping` — самая дешёвая команда, которая всё же ходит на сервер. Спрашивать
    // у клиента, «подключён» ли он, бесполезно: он считает соединение живым до первой неудачи.
    single<StorageHealth> {
        val database = get<MongoDatabase>()
        StorageHealth {
            database.runCommand(Document("ping", 1))
            true
        }
    }

    single { MongoUserRepository(get(), get()) }.bind<UserRepository>()
    single { MongoTokenRepository(get()) }.bind<TokenRepository>()
    single { MongoTransactionRepository(get()) }.bind<TransactionRepository>()
    single { MongoCategoryRepository(get()) }.bind<CategoryRepository>()
}
