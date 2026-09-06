package com.zubaer.maxvideoplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun libraryAndOpenFileControlRender() {
        rule.onNodeWithTag("open_file_button").assertIsDisplayed()
        rule.onNodeWithTag("network_url_input").assertIsDisplayed()
    }

    @Test fun activityRecreationDoesNotCrashFoundationUi() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("open_file_button").assertIsDisplayed()
    }
}
