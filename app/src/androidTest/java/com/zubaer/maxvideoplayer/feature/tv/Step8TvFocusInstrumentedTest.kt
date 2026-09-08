package com.zubaer.maxvideoplayer.feature.tv

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.keyDown
import androidx.compose.ui.test.keyUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step8TvFocusInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dpad_moves_focus_and_enter_activates_destination() {
        val focused = mutableStateOf(TvDestination.LIBRARY)
        val activated = mutableStateOf<TvDestination?>(null)
        compose.setContent {
            MaterialTheme {
                TvHomeScreen(
                    lastFocused = TvDestination.LIBRARY,
                    mountedUsbCount = 1,
                    onFocused = { focused.value = it },
                    onDestination = { activated.value = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tv_destination_library").assertIsFocused()
        compose.onNodeWithTag("tv_destination_library").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_destination_network").assertIsFocused()
        assertEquals(TvDestination.NETWORK, focused.value)

        compose.onNodeWithTag("tv_destination_network").performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitForIdle()
        assertEquals(TvDestination.NETWORK, activated.value)
    }

    @Test
    fun returning_tv_home_restores_previous_destination_focus() {
        compose.setContent {
            MaterialTheme {
                TvHomeScreen(
                    lastFocused = TvDestination.CLOUD,
                    mountedUsbCount = 0,
                    onFocused = {},
                    onDestination = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tv_destination_cloud").assertIsFocused()
    }
}
