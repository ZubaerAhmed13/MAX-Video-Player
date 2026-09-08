package com.zubaer.maxvideoplayer.playback

import android.media.MediaCodecList
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.device.DeviceDecoderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Produces the Step-6 CI emulator decoder inventory without treating it as universal Android support. */
@RunWith(AndroidJUnit4::class)
class Step6CodecCapabilityReportTest {

    @Test
    fun api35DecoderInventoryIsTruthfulAndExportable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val profile = app.container.deviceCapabilityProvider.collectDecoderProfile(forceRefresh = true)

        assertTrue(profile.apiLevel >= 23)
        assertTrue(profile.availableVideoDecoders.all { it.name.isNotBlank() && it.mimeType.startsWith("video/") })
        assertEquals(
            profile.availableVideoDecoders.map { it.mimeType }.distinct().sorted(),
            profile.supportedMimeTypes,
        )

        val platformDecoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .associateBy { it.name }
        profile.availableVideoDecoders.forEach { decoder ->
            val platform = platformDecoders[decoder.name]
            assertTrue("Inventory contains a decoder Android no longer reports: ${decoder.name}", platform != null)
            if (Build.VERSION.SDK_INT >= 29 && platform != null) {
                when (decoder.backend) {
                    DeviceDecoderBackend.HARDWARE -> assertTrue(platform.isHardwareAccelerated)
                    DeviceDecoderBackend.SOFTWARE -> assertTrue(platform.isSoftwareOnly)
                    DeviceDecoderBackend.UNKNOWN -> {
                        assertFalse(platform.isHardwareAccelerated)
                        assertFalse(platform.isSoftwareOnly)
                    }
                }
            }
        }

        val report = buildString {
            appendLine("MAX Video Player — Step 6 Decoder Capability Report")
            appendLine("API: ${profile.apiLevel}")
            appendLine("Device: ${profile.manufacturer} ${profile.model}")
            appendLine("ABIs: ${profile.abis.joinToString()}")
            appendLine("Video decoder entries: ${profile.availableVideoDecoders.size}")
            appendLine("Hardware decoder names: ${profile.hardwareDecoderNames.joinToString()}")
            appendLine("Software decoder names: ${profile.softwareDecoderNames.joinToString()}")
            appendLine("Unknown decoder names: ${profile.unknownDecoderNames.joinToString()}")
            appendLine("Supported video MIME types: ${profile.supportedMimeTypes.joinToString()}")
            appendLine()
            profile.availableVideoDecoders.forEach { decoder ->
                appendLine("codec=${decoder.name}")
                appendLine("  backend=${decoder.backend}")
                appendLine("  mime=${decoder.mimeType}")
                decoder.hardwareAccelerated?.let { appendLine("  hardwareAccelerated=$it") }
                decoder.softwareOnly?.let { appendLine("  softwareOnly=$it") }
                decoder.vendor?.let { appendLine("  vendor=$it") }
                appendLine("  adaptive=${decoder.adaptivePlayback}")
                appendLine("  secure=${decoder.securePlayback}")
                appendLine("  tunneled=${decoder.tunneledPlayback}")
                decoder.lowLatency?.let { appendLine("  lowLatency=$it") }
                appendLine("  profileLevels=${decoder.profileLevels.joinToString()}")
                appendLine("  colorFormats=${decoder.colorFormats.joinToString()}")
                decoder.resolutionTargets.forEach { (label, target) ->
                    appendLine("  $label=${target.width}x${target.height};30fps=${target.supportedAt30Fps};60fps=${target.supportedAt60Fps}")
                }
            }
        }

        context.openFileOutput(REPORT_FILE, android.content.Context.MODE_PRIVATE).bufferedWriter().use {
            it.write(report)
        }
        assertTrue(context.getFileStreamPath(REPORT_FILE).length() > 0L)

        // This report-export test is part of the API-35 certification lane. connectedDebugAndroidTest
        // removes the target package before the emulator-runner script resumes, so use UiAutomation's
        // explicit stdin pipe (API 31+) to let the shell own a durable copy in /data/local/tmp.
        assertTrue("Step-6 capability report export requires API 31+", Build.VERSION.SDK_INT >= 31)
        if (Build.VERSION.SDK_INT >= 31) {
            val reportBytes = report.toByteArray(Charsets.UTF_8)
            val descriptors = instrumentation.uiAutomation.executeShellCommandRw("cat > $SHELL_REPORT_FILE")
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { stdin ->
                    stdin.write(reportBytes)
                    stdin.flush()
                }
                ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { stdout ->
                    stdout.readBytes()
                }
            } finally {
                descriptors.forEach { descriptor -> runCatching { descriptor.close() } }
            }

            val exportedBytes = instrumentation.uiAutomation
                .executeShellCommand("wc -c $SHELL_REPORT_FILE")
                .use { descriptor ->
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                        reader.readText().trim().substringBefore(' ').toLongOrNull()
                    }
                }
                ?: 0L
            assertEquals(
                "Shell-owned Step-6 decoder capability report size differs from source report",
                reportBytes.size.toLong(),
                exportedBytes,
            )
        }
    }

    private companion object {
        const val REPORT_FILE = "step6-decoder-capability-report.txt"
        const val SHELL_REPORT_FILE = "/data/local/tmp/step6-decoder-capability-report.txt"
    }
}
