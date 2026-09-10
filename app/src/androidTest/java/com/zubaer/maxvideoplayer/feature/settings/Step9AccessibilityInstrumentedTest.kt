package com.zubaer.maxvideoplayer.feature.settings

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.privatevault.auth.AppLockController
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultAuthenticator
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.presentation.AppLockScreen
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerAction
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerInputController
import com.zubaer.maxvideoplayer.ui.MaxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step9AccessibilityInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsRemainScrollableAndReachableAtTwoHundredPercentFontScale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)
        val session = PrivateVaultSession()
        val authenticator = PrivateVaultAuthenticator(context, session)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaxTheme {
                    SettingsScreen(
                        repository = repository,
                        vaultAuthenticator = authenticator,
                        biometricConfigured = false,
                        onEnableBiometric = {},
                        onDisableBiometric = {},
                        onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Accessibility").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Settings import / export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reset all non-sensitive settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lockedSurfaceExposesOnlyGenericSemanticsNotPrivateTitles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val session = PrivateVaultSession()
        val authenticator = PrivateVaultAuthenticator(context, session)
        val controller = AppLockController(settings, authenticator, session)
        compose.setContent { MaxTheme { AppLockScreen(controller) } }

        compose.onNodeWithText("MAX Video Player locked").assertIsDisplayed()
        assertTrue(
            "Locked accessibility tree leaked private sentinel metadata",
            compose.onAllNodesWithText(
                "TOP_SECRET_PRIVATE_MOVIE_839247.mp4",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun keyboardAndDpadMapToTheExistingSinglePlaybackActionPolicy() {
        assertEquals(TvPlayerAction.PLAY_PAUSE, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(TvPlayerAction.SEEK_BACKWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvPlayerAction.SEEK_FORWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TvPlayerAction.SHOW_CONTROLS, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TvPlayerAction.BACK, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_ESCAPE))
    }
}
