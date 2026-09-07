package io.github.youndie.mani

import io.github.youndie.mani.config.MongoConfig
import io.github.youndie.mani.feature.category.CategoryRepository
import io.github.youndie.mani.feature.category.data.MongknCategoryRepository
import io.github.youndie.mani.feature.transaction.data.MongknTransactionRepository
import io.github.youndie.mani.feature.transaction.data.TransactionRepository
import io.github.youndie.mani.feature.user.data.MongknTokenRepository
import io.github.youndie.mani.feature.user.data.MongknUserRepository
import io.github.youndie.mani.feature.user.data.TokenRepository
import io.github.youndie.mani.feature.user.data.UserRepository
import io.github.youndie.mongkn.MongoClient
import io.github.youndie.mongkn.MongoDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Хранилище нативной сборки: mongkn поверх C-драйвера.
 *
 * Порты те же, что у JVM-сборки; отличается только реализация. Клиент один на процесс — он
 * владеет пулом соединений и своим пулом потоков, и второй экземпляр означал бы вдвое больше
 * и того и другого.
 */
fun mongknStorageModule(mongoConfig: MongoConfig): Module = module {
    single<MongoClient> { MongoClient(mongoConfig.connectionString) }
    single<MongoDatabase> { get<MongoClient>().getDatabase(mongoConfig.database) }

    single { MongknUserRepository(get(), get()) }.bind<UserRepository>()
    single { MongknTokenRepository(get()) }.bind<TokenRepository>()
    single { MongknTransactionRepository(get()) }.bind<TransactionRepository>()
    single { MongknCategoryRepository(get()) }.bind<CategoryRepository>()
}
