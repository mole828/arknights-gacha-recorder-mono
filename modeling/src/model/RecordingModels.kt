package model

import kotlinx.serialization.Serializable

@Serializable
data class HgToken(val content: String)

@Serializable
data class AccountInfo(
    val channelMasterId: Int,
    val channelName: String,
    val isDefault: Boolean,
    val isDeleted: Boolean,
    val isOfficial: Boolean,
    val nickName: String,
    val uid: Uid,
)

data class UserToken(
    val uid: Uid,
    val hgToken: HgToken,
    val nickName: String? = null,
)

data class GachaRecord(
    val uid: Uid,
    val gachaTs: ULong,
    val pos: UInt,
    val charId: String,
    val charName: String,
    val poolId: String,
    val poolName: String,
    val rarity: UInt,
    val isNew: Boolean,
)
