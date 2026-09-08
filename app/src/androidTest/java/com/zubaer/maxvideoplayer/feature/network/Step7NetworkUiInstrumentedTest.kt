package com.zubaer.maxvideoplayer.feature.network

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.presentation.LocationEditor
import com.zubaer.maxvideoplayer.feature.network.presentation.NetworkLocationDraft
import com.zubaer.maxvideoplayer.feature.network.presentation.OpenStreamForm
import org.junit.Rule
import org.junit.Test

class Step7NetworkUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cleartextHttpShowsWarningAndRequiresExplicitAcknowledgement() {
        composeRule.setContent {
            MaterialTheme { OpenStreamForm(onCancel = {}, onOpen = { _, _, _, _, _, _, _ -> }) }
        }
        composeRule.onNodeWithTag("stream_url").performTextInput("http://lan.example.test/movie.mp4")
        composeRule.onNodeWithText("This HTTP connection is not encrypted. Passwords, tokens, and media traffic can be intercepted.").assertExists()
        composeRule.onNodeWithText("Play").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("http_risk_ack").performScrollTo().performClick()
        composeRule.onNodeWithText("Play").assertIsEnabled()
    }

    @Test
    fun ftpEditorDisplaysTheCleartextWarning() {
        composeRule.setContent {
            MaterialTheme {
                LocationEditor(
                    draft = NetworkLocationDraft(displayName = "FTP", protocol = NetworkProtocol.FTP, host = "ftp.example.test", port = "21"),
                    onDraft = {}, onTest = {}, onSave = {}, onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("FTP does not encrypt your password or media traffic. Prefer FTPS, WebDAV over HTTPS, SMB3, or HTTPS.")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("I understand the cleartext security risk").assertExists()
    }

    @Test
    fun savedWebDavHttpRequiresExplicitAcknowledgement() {
        composeRule.setContent {
            MaterialTheme {
                LocationEditor(
                    draft = NetworkLocationDraft(displayName = "WebDAV HTTP", protocol = NetworkProtocol.WEBDAV_HTTP, host = "dav.example.test", port = "80"),
                    onDraft = {}, onTest = {}, onSave = {}, onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("HTTP does not encrypt your password or media traffic. Prefer FTPS, WebDAV over HTTPS, SMB3, or HTTPS.")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }
}
