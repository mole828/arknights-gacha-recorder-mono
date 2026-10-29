package model

interface GachaRepository {
    suspend fun upsertUser(accountInfo: AccountInfo, hgToken: HgToken)
    suspend fun updateUser(accountInfo: AccountInfo)
    suspend fun expireUid(uid: Uid)
    suspend fun expireToken(hgToken: HgToken)
    suspend fun setTokenExpired(hgToken: HgToken, expired: Boolean)
    suspend fun activeTokens(): List<UserToken>
    suspend fun users(): List<AccountInfo>
    suspend fun expiredUsers(): List<AccountInfo>
    suspend fun gachaPage(uid: Uid?, page: Long, pageSize: Int): GachasResponse
    suspend fun record(gacha: GachaRecord): UInt
}
