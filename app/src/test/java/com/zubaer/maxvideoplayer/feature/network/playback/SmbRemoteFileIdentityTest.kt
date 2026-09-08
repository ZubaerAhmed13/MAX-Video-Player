package com.zubaer.maxvideoplayer.feature.network.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbRemoteFileIdentityTest {
    private val original = SmbRemoteFileIdentity(
        sizeBytes = 5_000_000_000L,
        fileIndex = 42L,
        creationTime = 100L,
        lastWriteTime = 200L,
        changeTime = 300L,
    )

    @Test
    fun unchangedMetadataCanResumeAtTheCurrentLongOffset() {
        assertFalse(original.copy().hasChangedFrom(original))
    }

    @Test
    fun detectsEveryMetadataSignalAvailableFromSmb() {
        assertTrue(original.copy(sizeBytes = original.sizeBytes + 1L).hasChangedFrom(original))
        assertTrue(original.copy(fileIndex = original.fileIndex + 1L).hasChangedFrom(original))
        assertTrue(original.copy(creationTime = original.creationTime + 1L).hasChangedFrom(original))
        assertTrue(original.copy(lastWriteTime = original.lastWriteTime + 1L).hasChangedFrom(original))
        assertTrue(original.copy(changeTime = original.changeTime + 1L).hasChangedFrom(original))
    }
}
