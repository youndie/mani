package ru.workinprogress.feature.transaction.data

data class CategoryDb(@org.bson.codecs.pojo.annotations.BsonId val id: org.bson.types.ObjectId, val name: String)
