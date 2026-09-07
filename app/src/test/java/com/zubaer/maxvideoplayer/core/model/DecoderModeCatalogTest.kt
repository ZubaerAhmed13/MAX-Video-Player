package com.zubaer.maxvideoplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderModeCatalogTest {
    @Test fun historicalStep1SoftwareStatusRemainsTruthful() {
        val software = DecoderModeCatalog.step1.single { it.mode == DecoderMode.SOFTWARE }
        assertEquals(ImplementationStatus.PLANNED, software.status)
    }

    @Test fun allStep1ModesHaveTruthfulStatusEntries() {
        assertEquals(DecoderMode.entries.size, DecoderModeCatalog.step1.size)
        assertTrue(DecoderModeCatalog.step1.map { it.mode }.toSet().containsAll(DecoderMode.entries))
    }

    @Test fun step6ExposesAllFourModesAsImplementedWithDistinctPolicies() {
        assertEquals(DecoderMode.entries.size, DecoderModeCatalog.step6.size)
        assertEquals(DecoderMode.entries.toSet(), DecoderModeCatalog.step6.map { it.mode }.toSet())
        assertTrue(DecoderModeCatalog.step6.all { it.status == ImplementationStatus.IMPLEMENTED })
        assertEquals(4, DecoderModeCatalog.step6.map { it.note }.toSet().size)
    }
}
