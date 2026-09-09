package com.zubaer.maxvideoplayer.feature.cloud.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPkceTest {
    @Test
    fun verifier_and_state_are_url_safe_and_unique() {
        val verifierA = CloudPkce.newVerifier()
        val verifierB = CloudPkce.newVerifier()
        val stateA = CloudPkce.newState()
        val stateB = CloudPkce.newState()

        assertTrue(verifierA.length in 43..128)
        assertTrue(verifierA.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(stateA.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(verifierA, verifierB)
        assertNotEquals(stateA, stateB)
    }

    @Test
    fun challenge_is_deterministic_s256_and_state_compare_rejects_mismatch() {
        val verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~abc"
        val challenge = CloudPkce.challenge(verifier)

        assertEquals(challenge, CloudPkce.challenge(verifier))
        assertTrue(challenge.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(CloudPkce.constantTimeEquals("state-value", "state-value"))
        assertFalse(CloudPkce.constantTimeEquals("state-value", "state-other"))
    }
}
