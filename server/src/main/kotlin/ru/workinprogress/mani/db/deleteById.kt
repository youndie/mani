package ru.workinprogress.mani.db

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.types.ObjectId

suspend inline fun <T : Any> MongoCollection<T>.deleteById(id: String): Boolean =
    this.deleteOne(Filters.eq("_id", ObjectId(id))).deletedCount > 0
