package com.zubaer.maxvideoplayer.feature.usb

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkDataSourceRouter
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Step8UsbVirtualProviderTest {
    @Test
    fun contentPlaybackReadsCorrectBytesBeyondTwoGiBWithoutWholeFileCopy() {
        // The virtual provider exists only in the androidTest APK and is exported there so the
        // target app process can exercise the same production content:// routing path used for
        // removable SAF sources. The provider exposes a >3.2 GB sparse file, so this performs a
        // real 64-bit seek/read without allocating or buffering the entire logical file.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = NetworkDataSourceRouter.Factory(
            context,
            NetworkRequestRegistry(),
            CloudPlaybackRegistry(),
        ).createDataSource()
        val start = Step8LargeVirtualContentProvider.VERIFICATION_OFFSET
        val requested = Step8LargeVirtualContentProvider.VERIFICATION_LENGTH.toLong()
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
            val expected = ByteArray(actual.size) { index ->
                ((start + index.toLong()) and 0xff).toByte()
            }
            assertArrayEquals(expected, actual)
        } finally {
            source.close()
        }
    }

    @Test
    fun disconnectedRemovableProviderFailsCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = NetworkDataSourceRouter.Factory(
            context,
            NetworkRequestRegistry(),
            CloudPlaybackRegistry(),
        ).createDataSource()
        assertThrows(Exception::class.java) {
            source.open(
                DataSpec.Builder()
                    .setUri(Step8LargeVirtualContentProvider.REMOVED_URI)
                    .build(),
            )
        }
        runCatching { source.close() }
    }
}
