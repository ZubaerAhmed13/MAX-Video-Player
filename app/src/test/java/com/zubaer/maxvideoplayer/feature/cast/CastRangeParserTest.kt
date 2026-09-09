package com.zubaer.maxvideoplayer.feature.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastRangeParserTest {
    @Test
    fun rangeBeyondTwoGiB_remains64Bit() {
        val total = 3_221_225_473L
        val result = CastRangeParser.parse("bytes=2147483648-2147487743", total)
        assertEquals(
            RangeParseResult.Valid(ByteRange(2_147_483_648L, 2_147_487_743L)),
            result,
        )
    }

    @Test
    fun openEndedRange_isBoundedByKnownLength() {
        assertEquals(
            RangeParseResult.Valid(ByteRange(100L, 999L)),
            CastRangeParser.parse("bytes=100-", 1_000L),
        )
    }

    @Test
    fun suffixRange_isSupported() {
        assertEquals(
            RangeParseResult.Valid(ByteRange(900L, 999L)),
            CastRangeParser.parse("bytes=-100", 1_000L),
        )
    }

    @Test
    fun multiRange_isRejected() {
        assertEquals(
            RangeParseResult.Unsatisfiable(1_000L),
            CastRangeParser.parse("bytes=0-10,20-30", 1_000L),
        )
    }

    @Test
    fun relayTokens_have256BitHexShapeAndAreUnique() {
        val first = CastRelaySecurity.newSessionToken()
        val second = CastRelaySecurity.newSessionToken()
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)
    }
}
