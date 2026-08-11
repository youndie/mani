package ru.workinprogress.mani

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.category.data.MongoCategoryRepository
import ru.workinprogress.feature.transaction.data.MongoTransactionRepository
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.data.MongoTokenRepository
import ru.workinprogress.feature.user.data.MongoUserRepository
import ru.workinprogress.feature.user.data.TokenRepository
import ru.workinprogress.feature.user.data.UserRepository
import ru.workinprogress.mani.config.MongoConfig

/**
 * Хранилище JVM-сборки: официальный драйвер MongoDB.
 *
 * Второй такой модуль есть в `:server-native` — на mongkn. Общими остаются только порты, и это
 * не дублирование, а единственный способ иметь две сборки: драйвер существует лишь на JVM,
 * mongkn — лишь под linuxX64.
 */
fun mongoStorageModule(mongoConfig: MongoConfig): Module =
    module {
        single<MongoClient> { MongoClient.create(mongoConfig.connectionString) }
        single<MongoDatabase> { get<MongoClient>().getDatabase(mongoConfig.database) }

        single { MongoUserRepository(get(), get()) }.bind<UserRepository>()
        single { MongoTokenRepository(get()) }.bind<TokenRepository>()
        single { MongoTransactionRepository(get()) }.bind<TransactionRepository>()
        single { MongoCategoryRepository(get()) }.bind<CategoryRepository>()
    }
