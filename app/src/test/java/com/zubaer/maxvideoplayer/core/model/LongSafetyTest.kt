package com.zubaer.maxvideoplayer.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class LongSafetyTest {
    @Test fun threeGigabyteValuesDoNotRequireIntArithmetic() {
        val bytes = 3L * 1024L * 1024L * 1024L
        assertTrue(bytes > Int.MAX_VALUE.toLong())
        val middle = bytes / 2L
        val nearEnd = (bytes - 1024L * 1024L).coerceAtLeast(0L)
        assertTrue(middle > 0L)
        assertTrue(nearEnd > Int.MAX_VALUE.toLong())
    }
}
