package com.zubaer.maxvideoplayer.feature.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateVaultCastPolicyTest {
    @Test
    fun maxvaultIsAlwaysUnsupportedAndNeverFallsThroughToRelay() {
        val resolver = CastSourceResolver()
        val decision = resolver.resolve(
            "maxvault://00000000-0000-0000-0000-000000000001",
            receiverReachable = false,
            requiresPrivateHeaders = true,
        )
        assertEquals(CastSourceMode.UNSUPPORTED_CAST, decision.mode)
        assertTrue(decision.reason.contains("Private Vault"))
    }
}
