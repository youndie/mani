package io.github.youndie.mani.feature.user.data

import io.github.youndie.mani.feature.transaction.data.CategoryDb
import io.github.youndie.mani.feature.user.User
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class UserDb(
    @BsonId val id: ObjectId,
    val username: String,
    val password: String,
    val salt: String?,
    val tokens: List<String>,
    val categories: List<CategoryDb>?,
) {
    companion object {
        fun UserDb.fromDb() = User(this.id.toHexString(), this.username)
    }
}
