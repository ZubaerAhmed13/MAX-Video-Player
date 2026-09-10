package com.zubaer.maxvideoplayer.feature.audio

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAccessibilityPolicyTest {
    @Test
    fun descriptiveAndCommentaryRoleFlagsProduceExplicitLabels() {
        val flags = C.ROLE_FLAG_DESCRIBES_VIDEO or C.ROLE_FLAG_COMMENTARY
        assertTrue(AudioAccessibilityPolicy.isAudioDescription(flags))
        assertTrue(AudioAccessibilityPolicy.isCommentary(flags))
        val label = AudioAccessibilityPolicy.readableLabel("English", commentary = true, audioDescription = true)
        assertTrue(label.contains("Audio description"))
        assertTrue(label.contains("Commentary"))
    }

    @Test
    fun ordinaryTrackIsNotMislabelled() {
        assertFalse(AudioAccessibilityPolicy.isAudioDescription(0))
        assertFalse(AudioAccessibilityPolicy.isCommentary(0))
        assertTrue(AudioAccessibilityPolicy.readableLabel("English", false, false) == "English")
    }
}
