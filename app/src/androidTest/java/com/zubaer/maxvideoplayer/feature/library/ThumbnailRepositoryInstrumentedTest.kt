package com.zubaer.maxvideoplayer.feature.library

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = ThumbnailRepository(context)

    @Test fun unsupportedNetworkAndMissingContentSourcesFailToPlaceholderInsteadOfThrowing() = runTest {
        val network = media("network", "https://example.invalid/video.mp4", MediaSourceType.NETWORK)
        val missing = media("missing", "content://example.invalid/missing/video", MediaSourceType.SAF)

        assertNull(repository.load(network, 150, 84))
        assertNull(repository.load(missing, 150, 84))
    }

    @Test fun thumbnailRequestIsCancellable() = runTest {
        val missing = media("cancel", "content://example.invalid/missing/video", MediaSourceType.SAF)
        val job = launch { repository.load(missing, 640, 480) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun media(id: String, uri: String, sourceType: MediaSourceType) = AppMedia(
        stableId = id,
        uri = uri,
        title = id,
        mimeType = "video/mp4",
        sourceType = sourceType,
    )
}
