package model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GachaSerializationTest {
    @Test
    fun sharedAccountKeepsExistingHttpAndRpcFields() {
        val payload = """{"channelMasterId":1,"channelName":"官服","isDefault":true,"isDeleted":false,"isOfficial":true,"nickName":"Doctor","uid":"123"}"""
        val account = AccountInfo(1, "官服", true, false, true, "Doctor", "123")
        assertEquals(account, Json.decodeFromString<rpc.AgentAccountInfo>(payload))
        assertEquals(payload, Json.encodeToString(account))
    }

    private val json = Json

    @Test
    fun gachaResponseKeepsUnsignedTimestampAndPaginationNumbers() {
        val response = GachasResponse(
            data = GachasResponseData(
                list = listOf(
                    Draw(
                        chars = listOf(Char(isNew = false, rarity = 5u, name = "Exusiai")),
                        nickName = "doctor",
                        pool = "Banner",
                        ts = ULong.MAX_VALUE,
                        uid = "123456789",
                    )
                ),
                pagination = Pagination(
                    current = 9007199254740993uL,
                    pageSize = 10,
                    total = ULong.MAX_VALUE,
                ),
            )
        )
        val expected = """{"data":{"list":[{"chars":[{"isNew":false,"rarity":5,"name":"Exusiai"}],"nickName":"doctor","pool":"Banner","ts":18446744073709551615,"uid":"123456789"}],"pagination":{"current":9007199254740993,"pageSize":10,"total":18446744073709551615}}}"""

        assertEquals(expected, json.encodeToString(response))
        assertEquals(response, json.decodeFromString<GachasResponse>(expected))
    }

}
