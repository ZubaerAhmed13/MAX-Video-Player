package com.zubaer.maxvideoplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderModeCatalogTest {
    @Test fun softwareDecoderIsNotFalselyMarkedImplemented() {
        val software = DecoderModeCatalog.step1.single { it.mode == DecoderMode.SOFTWARE }
        assertEquals(ImplementationStatus.PLANNED, software.status)
    }

    @Test fun allModesHaveTruthfulStatusEntries() {
        assertEquals(DecoderMode.entries.size, DecoderModeCatalog.step1.size)
        assertTrue(DecoderModeCatalog.step1.map { it.mode }.toSet().containsAll(DecoderMode.entries))
    }
}
