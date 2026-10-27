package top.moles

typealias Rarity = Int

@kotlinx.serialization.Serializable
data class Char(
    val isNew: Boolean,
    val name: String,
    val rarity: Rarity
)

@kotlinx.serialization.Serializable
data class Gacha(
    val chars: List<Char>,
    val nickName: String,
    val pool: String,
    val ts: Long,
    val uid: String,
)

@kotlinx.serialization.Serializable
data class Pagination (
    val current: UInt,
    val pageSize: UInt,
    val total: UInt,
)

@kotlinx.serialization.Serializable
data class GachaPage(
    val list: List<Gacha>,
    val pagination: Pagination,
)

@kotlinx.serialization.Serializable
data class GachaResponse(
    val data: GachaPage,
)

@kotlinx.serialization.Serializable
data class User(
    val uid: String,
    val nickName: String,
)