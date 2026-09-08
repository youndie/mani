package io.github.youndie.mani.feature.transaction

/**
 * У записи есть идентификатор, выданный сервером.
 *
 * Часть контракта, а не клиентской базы: его реализуют [Transaction] и [Category], и без него
 * модель не собирается.
 */
interface WithId {
    val id: String
}
