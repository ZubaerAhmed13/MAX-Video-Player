package com.zubaer.maxvideoplayer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun releaseLibraryChromeRendersWithoutPermanentSearchOrLegacyTopStrip() {
        rule.onNodeWithTag("library_search_button").assertIsDisplayed()
        // The interactive parent owns merged display bounds; the tagged Canvas child certifies
        // that the release glyph is composed instead of a Unicode/text pseudo-icon.
        rule.onNodeWithTag("library_search_icon").assertExists()
        rule.onNodeWithTag("library_view_button").assertIsDisplayed()
        rule.onNodeWithTag("library_view_icon").assertExists()
        rule.onNodeWithTag("library_more_button").assertIsDisplayed()
        rule.onNodeWithTag("library_more_icon").assertExists()
        rule.onNodeWithTag("library_section_row").assertIsDisplayed()
        rule.onNodeWithTag("section_folders").assertExists()
        rule.onNodeWithTag("section_private").assertExists()
        rule.onNodeWithTag("section_network").assertExists()
        rule.onNodeWithTag("section_cloud").assertExists()

        // LazyRow intentionally virtualizes off-screen source chips. Scroll to the release-critical
        // USB and Playlists entries before asserting visibility rather than assuming all seven chips
        // are composed on a phone-width viewport at once.
        rule.onNodeWithTag("library_section_row").performScrollToIndex(5)
        rule.waitForIdle()
        rule.onNodeWithTag("section_usb").assertIsDisplayed()
        rule.onNodeWithTag("library_section_row").performScrollToIndex(6)
        rule.waitForIdle()
        rule.onNodeWithTag("section_playlists").assertIsDisplayed()

        rule.onNodeWithTag("library_search_input").assertDoesNotExist()
        rule.onNodeWithTag("network_url_input").assertDoesNotExist()
    }

    @Test
    fun primarySourcesAndOverflowSectionsNavigateWithoutSeededMedia() {
        selectSource(index = 0, tag = "section_folders")
        rule.onNodeWithTag("folder_list").assertIsDisplayed()

        selectSource(index = 6, tag = "section_playlists")
        rule.onNodeWithTag("playlist_list").assertIsDisplayed()

        openOverflowSection("Continue watching")
        rule.onNodeWithText("Continue watching").assertExists()

        openOverflowSection("Favourites")
        rule.onNodeWithText("Favourites").assertExists()

        openOverflowSection("History")
        rule.onNodeWithText("History").assertExists()
    }

    @Test
    fun playlistCreateFlowUsesPersistentProfessionalSurface() {
        selectSource(index = 6, tag = "section_playlists")
        rule.onNodeWithTag("playlist_list").assertIsDisplayed()
        rule.onNodeWithTag("new_playlist_input").performTextInput("Instrumentation Playlist")
        rule.onNodeWithTag("create_playlist_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_list").assertIsDisplayed()
    }

    @Test
    fun searchIsProgressivelyDisclosedAndBackRestoresCompactAppBar() {
        rule.onNodeWithTag("library_search_input").assertDoesNotExist()
        rule.onNodeWithTag("library_search_button").assertIsDisplayed().performClick()
        rule.waitForIdle()

        val search = rule.onNodeWithTag("library_search_input")
        search.assertIsDisplayed().performTextInput("definitely missing")
        search.performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("clear_search_button").assertIsDisplayed()
        rule.onNodeWithTag("clear_search_icon").assertExists()
        rule.onNodeWithTag("clear_search_button").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasTestTag("clear_search_button")).assertCountEquals(0)

        search.performTextInput("another query")
        rule.waitForIdle()
        rule.onNodeWithTag("clear_search_button").assertIsDisplayed()
        rule.onNodeWithTag("clear_search_icon").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("library_search_input").assertDoesNotExist()
        rule.onNodeWithTag("library_search_button").assertIsDisplayed()
    }

    @Test
    fun networkUrlEntryLivesInOverflowInsteadOfPermanentLibraryChrome() {
        rule.onNodeWithTag("network_url_input").assertDoesNotExist()
        rule.onNodeWithTag("library_more_button").performClick()
        rule.onNodeWithText("Open network URL").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("network_url_input").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun activityRecreationKeepsReleaseLibraryChromeUsable() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("library_search_button").assertIsDisplayed()
        rule.onNodeWithTag("library_search_icon").assertExists()
        rule.onNodeWithTag("library_more_button").assertIsDisplayed()
        rule.onNodeWithTag("library_more_icon").assertExists()
        rule.onNodeWithTag("library_section_row").assertIsDisplayed()
    }

    private fun selectSource(index: Int, tag: String) {
        rule.onNodeWithTag("library_section_row").performScrollToIndex(index)
        rule.waitForIdle()
        rule.onNodeWithTag(tag).assertIsDisplayed().performClick()
        rule.waitForIdle()
    }

    private fun openOverflowSection(label: String) {
        rule.onNodeWithTag("library_more_button").assertIsDisplayed().performClick()
        rule.onNodeWithText(label).assertIsDisplayed().performClick()
        rule.waitForIdle()
    }
}
