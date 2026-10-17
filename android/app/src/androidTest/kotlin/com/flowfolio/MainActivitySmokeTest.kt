package com.flowfolio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.feature.library.LibraryTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test: launches the real app (with Hilt) on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andShowsLibraryWithImport() {
        compose.onNodeWithTag(LibraryTestTags.IMPORT).assertIsDisplayed()
    }
}
