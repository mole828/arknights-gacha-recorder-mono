package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GachaPageRequestTest {
    @Test
    fun zeroBasedPagesProduceValidOffsets() {
        assertEquals(0L, GachaPageRequest().page)
        assertEquals(0L, GachaPageRequest().offset)
        assertEquals(20L, GachaPageRequest(2, 10).offset)
        assertEquals(Long.MAX_VALUE, GachaPageRequest(Long.MAX_VALUE, 1).offset)
        assertEquals(Long.MAX_VALUE / 10 * 10, GachaPageRequest(Long.MAX_VALUE / 10, 10).offset)
    }

    @Test
    fun rejectsNegativePagesInvalidSizesAndOverflow() {
        assertFailsWith<IllegalArgumentException> { GachaPageRequest(-1) }
        assertFailsWith<IllegalArgumentException> { GachaPageRequest(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { GachaPageRequest(pageSize = -1) }
        assertFailsWith<IllegalArgumentException> { GachaPageRequest(Long.MAX_VALUE / 10 + 1, 10) }
    }
}
