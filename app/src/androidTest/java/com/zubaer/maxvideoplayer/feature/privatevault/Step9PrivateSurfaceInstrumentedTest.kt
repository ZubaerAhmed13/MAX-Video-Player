package com.zubaer.maxvideoplayer.feature.privatevault

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step9PrivateSurfaceInstrumentedTest {
    @Test
    fun privateSurfaceTogglesAndroidSecureWindowFlagWithoutChangingOrdinarySurfacePolicy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setPrivateSurfaceProtected(true)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                activity.setPrivateSurfaceProtected(false)
                assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun privateMediaCannotEnterPictureInPictureThroughActivityEntryPoint() {
        val privateMedia = AppMedia(
            stableId = "maxvault://00000000-0000-0000-0000-000000000001",
            uri = "maxvault://00000000-0000-0000-0000-000000000001",
            title = "Private media",
            sourceType = MediaSourceType.PRIVATE,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.enterPip(privateMedia)
                assertFalse(activity.isInPictureInPictureMode)
                assertEquals(MediaSourceType.PRIVATE, privateMedia.sourceType)
            }
        }
    }
}
