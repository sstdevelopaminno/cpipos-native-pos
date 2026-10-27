package com.cpipos.pos.next.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cpipos.pos.next.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real MainActivity on an emulator. No demo tenant, credentials,
 * payments or production data are needed. This does not certify live merchant E2E.
 */
@RunWith(AndroidJUnit4::class)
class NativeLoginLanguageInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginStartsAndSwitchesThaiEnglishLaoAndPersistsSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("cpipos_native_ui", Context.MODE_PRIVATE)

        // Real login controls must render within a bounded time, even without
        // a network session; it is a smoke gate, not a performance benchmark.
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("EN").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("EN").performClick()
        compose.onNodeWithText("Store code").assertIsDisplayed()
        compose.runOnIdle { assertEquals("en", prefs.getString("language", null)) }

        compose.onNodeWithText("ລາວ").performClick()
        compose.onNodeWithText("ລະຫັດຮ້ານ").assertIsDisplayed()
        compose.runOnIdle { assertEquals("lo", prefs.getString("language", null)) }

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("ລະຫັດຮ້ານ").assertIsDisplayed()

        compose.onNodeWithText("ไทย").performClick()
        compose.onNodeWithText("รหัสร้านค้า").assertIsDisplayed()
        compose.runOnIdle { assertEquals("th", prefs.getString("language", null)) }
    }
}
