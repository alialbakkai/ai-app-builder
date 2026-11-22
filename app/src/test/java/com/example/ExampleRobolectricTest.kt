package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches tasmee arabic resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("تسميع", appName)
    }

    @Test
    fun `home screen verifies qaloon mushaf and navigates to free recitation mode`() {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag("mushaf_verified_badge").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mode_free_card").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_surah_card").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_hidden_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("mode_free_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("primary_mic_button").assertIsDisplayed()
        composeRule.onNodeWithTag("finish_session_button").assertIsDisplayed()
    }
}
