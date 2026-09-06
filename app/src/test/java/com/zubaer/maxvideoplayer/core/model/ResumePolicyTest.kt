package com.zubaer.maxvideoplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {
    @Test fun nearBeginningStartsOver() {
        assertEquals(ResumeAction.START_OVER, ResumePolicy.decide(5_000L, 120_000L).action)
    }

    @Test fun meaningfulProgressOffersResume() {
        val decision = ResumePolicy.decide(60_000L, 180_000L)
        assertEquals(ResumeAction.OFFER_RESUME, decision.action)
        assertEquals(60_000L, decision.positionMs)
    }

    @Test fun nearEndIsCompleted() {
        assertEquals(ResumeAction.COMPLETED, ResumePolicy.decide(178_000L, 180_000L).action)
    }
}
