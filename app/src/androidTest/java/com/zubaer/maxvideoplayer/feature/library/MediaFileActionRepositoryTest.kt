package com.zubaer.maxvideoplayer.feature.library

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFileActionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = MediaFileActionRepository(context.contentResolver)

    @Test fun networkMediaCannotBeDeletedOrRenamedAsLocalFile() = runTest {
        val network = AppMedia(
            stableId = "network",
            uri = "https://example.invalid/video.mp4",
            title = "Network",
            mimeType = "video/mp4",
            sourceType = MediaSourceType.NETWORK,
        )

        assertTrue(repository.delete(network) is MediaFileActionRepository.Result.Unsupported)
        assertTrue(repository.rename(network, "renamed.mp4") is MediaFileActionRepository.Result.Unsupported)
    }

    @Test fun blankRenameIsRejectedWithoutTouchingProvider() = runTest {
        val media = AppMedia(
            stableId = "saf",
            uri = "content://example.invalid/document/video",
            title = "Video",
            mimeType = "video/mp4",
            sourceType = MediaSourceType.SAF,
        )

        assertTrue(repository.rename(media, "   ") is MediaFileActionRepository.Result.Failed)
    }
}
