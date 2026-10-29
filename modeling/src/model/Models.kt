package model

import kotlinx.serialization.Serializable

typealias Uid = String

@Serializable
data class RegisterRequest(val token: String)

@Serializable
data class RegisterResponse(
    val msg: String,
    val uid: Uid? = null,
)

@Serializable
data class Char(
    val isNew: Boolean,
    val rarity: UInt,
    val name: String,
)

@Serializable
data class Draw(
    val chars: List<Char>,
    val nickName: String,
    val pool: String,
    val ts: ULong,
    val uid: Uid,
)

@Serializable
data class Pagination(
    val current: ULong,
    val pageSize: Int,
    val total: ULong,
)

@Serializable
data class GachasResponseData(
    val list: List<Draw>,
    val pagination: Pagination,
)

@Serializable
data class GachasResponse(
    val data: GachasResponseData,
)

@Serializable
data class User(
    val uid: Uid,
    val nickName: String,
)

/** Zero-based page request; the offset must fit the database's signed bigint. */
data class GachaPageRequest(val page: Long = 0, val pageSize: Int = 10) {
    init {
        require(page >= 0) { "page must be non-negative" }
        require(pageSize > 0) { "pageSize must be positive" }
        require(page <= Long.MAX_VALUE / pageSize) { "page offset is too large" }
    }

    val offset: Long get() = page * pageSize
}
