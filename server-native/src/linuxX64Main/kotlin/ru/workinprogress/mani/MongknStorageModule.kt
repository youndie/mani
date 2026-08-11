package ru.workinprogress.mani

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.feature.category.CategoryRepository
import ru.workinprogress.feature.category.data.MongknCategoryRepository
import ru.workinprogress.feature.transaction.data.MongknTransactionRepository
import ru.workinprogress.feature.transaction.data.TransactionRepository
import ru.workinprogress.feature.user.data.MongknTokenRepository
import ru.workinprogress.feature.user.data.MongknUserRepository
import ru.workinprogress.feature.user.data.TokenRepository
import ru.workinprogress.feature.user.data.UserRepository
import ru.workinprogress.mani.config.MongoConfig
import ru.workinprogress.mongkn.MongoClient
import ru.workinprogress.mongkn.MongoDatabase

/**
 * Хранилище нативной сборки: mongkn поверх C-драйвера.
 *
 * Порты те же, что у JVM-сборки; отличается только реализация. Клиент один на процесс — он
 * владеет пулом соединений и своим пулом потоков, и второй экземпляр означал бы вдвое больше
 * и того и другого.
 */
fun mongknStorageModule(mongoConfig: MongoConfig): Module =
    module {
        single<MongoClient> { MongoClient(mongoConfig.connectionString) }
        single<MongoDatabase> { get<MongoClient>().getDatabase(mongoConfig.database) }

        single { MongknUserRepository(get(), get()) }.bind<UserRepository>()
        single { MongknTokenRepository(get()) }.bind<TokenRepository>()
        single { MongknTransactionRepository(get()) }.bind<TransactionRepository>()
        single { MongknCategoryRepository(get()) }.bind<CategoryRepository>()
    }
