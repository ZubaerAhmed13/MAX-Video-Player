package com.zubaer.maxvideoplayer.feature.usb

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkDataSourceRouter
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Step8UsbVirtualProviderTest {
    @After
    fun restoreSource() {
        Step8LargeVirtualContentProvider.available = true
    }

    @Test
    fun contentPlaybackReadsCorrectBytesBeyondTwoGiBWithoutWholeFileCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = NetworkDataSourceRouter.Factory(
            context,
            NetworkRequestRegistry(),
            CloudPlaybackRegistry(),
        ).createDataSource()
        val start = 2_147_483_648L + 33_333L
        val requested = 8192L
        val resolved = source.open(
            DataSpec.Builder()
                .setUri(Step8LargeVirtualContentProvider.URI)
                .setPosition(start)
                .setLength(requested)
                .build(),
        )
        try {
            assertEquals(requested, resolved)
            val actual = ByteArray(requested.toInt())
            var offset = 0
            while (offset < actual.size) {
                val read = source.read(actual, offset, actual.size - offset)
                if (read < 0) break
                offset += read
            }
            assertEquals(actual.size, offset)
            val expected = ByteArray(actual.size) { index -> ((start + index.toLong()) and 0xff).toByte() }
            assertArrayEquals(expected, actual)
        } finally {
            source.close()
        }
    }

    @Test
    fun disconnectedRemovableProviderFailsCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Step8LargeVirtualContentProvider.available = false
        val source = NetworkDataSourceRouter.Factory(
            context,
            NetworkRequestRegistry(),
            CloudPlaybackRegistry(),
        ).createDataSource()
        assertThrows(Exception::class.java) {
            source.open(DataSpec.Builder().setUri(Step8LargeVirtualContentProvider.URI).build())
        }
        runCatching { source.close() }
    }
}
