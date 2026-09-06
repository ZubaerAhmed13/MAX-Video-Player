package com.zubaer.maxvideoplayer.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.core.device.DeviceCapabilityProvider
import com.zubaer.maxvideoplayer.core.media.MediaMetadataExtractor
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationMappingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun networkMetadataMappingIsStableAndNonDestructive() {
        val url = "https://example.com/media/sample.m3u8"
        val first = MediaMetadataExtractor(context).fromNetworkUrl(url)
        val second = MediaMetadataExtractor(context).fromNetworkUrl(url)

        assertEquals(MediaSourceType.NETWORK, first.sourceType)
        assertEquals(url, first.uri)
        assertEquals("sample.m3u8", first.title)
        assertTrue(first.stableId.isNotBlank())
        assertEquals(first.stableId, second.stableId)
    }

    @Test
    fun deviceCapabilityProfileMapsRuntimeWithoutInventingSupport() {
        val profile = DeviceCapabilityProvider(context).collect()

        assertTrue(profile.apiLevel >= 23)
        assertTrue(profile.cpuCoreCount > 0)
        assertTrue(profile.totalMemoryBytes > 0L)
        assertTrue(profile.availableMemoryBytes >= 0L)
        assertFalse(profile.abis.isEmpty())

        profile.codecs.forEach { codec ->
            assertTrue(codec.name.isNotBlank())
            assertFalse(codec.mimeTypes.isEmpty())
            codec.resolutionTargets.values.forEach { resolution ->
                assertTrue(resolution.width > 0)
                assertTrue(resolution.height > 0)
            }
        }
    }
}
