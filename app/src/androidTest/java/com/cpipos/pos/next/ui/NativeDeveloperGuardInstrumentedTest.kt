package com.cpipos.pos.next.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cpipos.pos.next.core.security.AndroidDeveloperSignals
import com.cpipos.pos.next.core.security.NativeAndroidDeveloperGuard
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware-independent Android security regression. Runs on API 27 and 35
 * emulators, not physical POS terminals. Never changes adb_enabled because
 * disabling ADB would disconnect the test runner.
 */
@RunWith(AndroidJUnit4::class)
class NativeDeveloperGuardInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String {
        val output: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText().trim() }
    }

    private fun setGlobal(name: String, value: String) {
        if (value == "null") shell("settings delete global $name")
        else shell("settings put global $name $value")
    }

    @Test
    fun detectsDeveloperOptionsAndWirelessDebuggingFromAndroidGlobalSettings() {
        val keys = listOf("development_settings_enabled", "adb_wifi_enabled")
        val initial = keys.associateWith { shell("settings get global $it") }
        try {
            shell("settings put global development_settings_enabled 0")
            shell("settings put global adb_wifi_enabled 0")
            var state = NativeAndroidDeveloperGuard.inspect(context)
            assertFalse(state.developerOptions)
            assertFalse(state.wirelessDebugging)

            shell("settings put global development_settings_enabled 1")
            state = NativeAndroidDeveloperGuard.inspect(context)
            assertTrue(state.developerOptions)
            assertTrue(state.detected)

            shell("settings put global development_settings_enabled 0")
            shell("settings put global adb_wifi_enabled 1")
            state = NativeAndroidDeveloperGuard.inspect(context)
            assertTrue(state.wirelessDebugging)
            assertTrue(state.detected)
        } finally {
            keys.forEach { setGlobal(it, initial.getValue(it)) }
        }
    }

    @Test
    fun persistsAndIsolatesTheITPolicyPerTenant() {
        val tenantA = UUID.randomUUID().toString()
        val tenantB = UUID.randomUUID().toString()
        assertNull(NativeAndroidDeveloperGuard.cachedEnabled(context, tenantA))
        assertNull(NativeAndroidDeveloperGuard.cachedEnabled(context, tenantB))
        assertTrue(NativeAndroidDeveloperGuard.rememberEnabled(context, tenantA, true))
        assertEquals(true, NativeAndroidDeveloperGuard.cachedEnabled(context, tenantA))
        assertNull(NativeAndroidDeveloperGuard.cachedEnabled(context, tenantB))
        assertTrue(NativeAndroidDeveloperGuard.rememberEnabled(context, tenantB, false))
        assertEquals(false, NativeAndroidDeveloperGuard.cachedEnabled(context, tenantB))
        assertEquals(true, NativeAndroidDeveloperGuard.cachedEnabled(context, tenantA))
    }

    @Test
    fun showsAndRemovesNonDismissibleWarningWhenSignalsChange() {
        val flags = mutableStateOf(AndroidDeveloperSignals(false, false, false))
        compose.setContent { NativeDeveloperGuardDialog(signals = flags.value) }
        compose.onNodeWithText("กรุณาปิดโหมดนักพัฒนา").assertDoesNotExist()
        compose.runOnIdle { flags.value = AndroidDeveloperSignals(true, false, false) }
        compose.onNodeWithText("กรุณาปิดโหมดนักพัฒนา").assertIsDisplayed()
        compose.onNodeWithText("เปิดการตั้งค่า Android").assertIsDisplayed()
        compose.runOnIdle { flags.value = AndroidDeveloperSignals(false, false, false) }
        compose.onNodeWithText("กรุณาปิดโหมดนักพัฒนา").assertDoesNotExist()
    }
}
