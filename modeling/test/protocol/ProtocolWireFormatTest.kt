package protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import model.Char
import model.Draw
import model.GachasResponse
import model.GachasResponseData
import model.Pagination

class ProtocolWireFormatTest {
    private val json = Json

    @Test
    fun authAndNullableTaskKeepTheirWireShape() {
        assertWireCompatible(
            MessageTemplate.Auth(agentKey = "agent-key"),
            """{"type":"auth","agentKey":"agent-key"}""",
        )
        assertWireCompatible(
            MessageTemplate.Task(
                hgToken = AgentHgToken(content = "hg-token"),
                uid = null,
            ),
            """{"type":"task","hgToken":{"content":"hg-token"},"uid":null}""",
        )
    }

    @Test
    fun taskResultAndUserInfoKeepTheirWireShape() {
        assertWireCompatible(
            MessageTemplate.TaskResult(
                result = listOf(
                    AgentGachaInfo(
                        charId = "char-1",
                        charName = "Exusiai",
                        gachaTs = ULong.MAX_VALUE,
                        isNew = true,
                        poolId = "pool-1",
                        poolName = "Banner",
                        pos = 9u,
                        rarity = 5u,
                    )
                ),
                uid = "123456789",
                hgToken = AgentHgToken(content = "hg-token"),
            ),
            """{"type":"task_result","result":[{"charId":"char-1","charName":"Exusiai","gachaTs":18446744073709551615,"isNew":true,"poolId":"pool-1","poolName":"Banner","pos":9,"rarity":5}],"uid":"123456789","hgToken":{"content":"hg-token"}}""",
        )
        assertWireCompatible(
            MessageTemplate.UserInfo(
                info = AgentAccountInfo(
                    channelMasterId = 1,
                    channelName = "official",
                    isDefault = true,
                    isDeleted = false,
                    isOfficial = true,
                    nickName = "doctor",
                    uid = "123456789",
                ),
                hgToken = AgentHgToken(content = "hg-token"),
            ),
            """{"type":"user_info","info":{"channelMasterId":1,"channelName":"official","isDefault":true,"isDeleted":false,"isOfficial":true,"nickName":"doctor","uid":"123456789"},"hgToken":{"content":"hg-token"}}""",
        )
    }

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

    private fun assertWireCompatible(message: MessageTemplate, expected: String) {
        assertEquals(expected, json.encodeToString(message))
        assertEquals(message, json.decodeFromString<MessageTemplate>(expected))
    }
}
