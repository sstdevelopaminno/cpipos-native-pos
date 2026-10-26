package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBillingLanguageContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun billingUsesCanonicalCpiposApisWithoutShift() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        assertTrue(client.contains("/api/pos/billing/overview"))
        assertTrue(client.contains("/api/pos/billing/requests"))
        assertTrue(client.contains("WebSubscriptionOverview"))
        assertTrue(client.contains("WebSubscriptionSlip"))

        listOf("subscriptionOverview", "submitSubscriptionRequest").forEach { method ->
            val start = client.indexOf("fun $method")
            assertTrue("Missing $method", start >= 0)
            val window = client.substring(start, kotlin.math.min(client.length, start + 1500))
            assertTrue("$method must work without open shift", window.contains("requireShift = false"))
            assertTrue("$method must not require sale permission", window.contains("requireSell = false"))
        }
        assertTrue(client.contains("require(session.role == \"owner\")"))
        assertTrue(client.contains("X-CpIPOS-Client"))
        assertTrue(client.contains("native-android"))
    }

    @Test
    fun packagePaymentAlwaysRemainsReachable() {
        val more = source("src/main/java/com/cpipos/pos/next/ui/NativeMoreScreen.kt")
        assertTrue(more.contains("\"billing\""))
        assertTrue(more.contains("ic_package_payment"))
        assertTrue(more.contains("if (menu.key == \"billing\") true"))
        assertTrue(more.contains("bottom = 156.dp"))
        assertTrue(more.contains("bottom = 150.dp"))
    }

    @Test
    fun languageSelectionChangesLiveNativeMenus() {
        val language = source("src/main/java/com/cpipos/pos/next/ui/NativeUiLanguage.kt")
        val bottomNav = source("src/main/java/com/cpipos/pos/next/ui/MobilePosPreviewScreen.kt")
        val settings = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsHubScreen.kt")
        val more = source("src/main/java/com/cpipos/pos/next/ui/NativeMoreScreen.kt")

        assertTrue(language.contains("setOf(\"th\", \"en\", \"lo\")"))
        assertTrue(language.contains("languageState.value = code"))
        assertTrue(bottomNav.contains("nativeUiText(\"nav.sale\""))
        assertTrue(bottomNav.contains("nativeUiText(\"nav.stock\""))
        assertTrue(settings.contains("nativeUiText(\"settings.title\""))
        assertTrue(more.contains("nativeUiText(\"more.title\""))
    }

    @Test
    fun billingSlipWorkIsBoundedAndOffMainThread() {
        val screen = source("src/main/java/com/cpipos/pos/next/ui/NativeSubscriptionBillingScreen.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        assertTrue(screen.contains("withContext(Dispatchers.IO)"))
        assertTrue(screen.contains("4 * 1024 * 1024"))
        assertTrue(screen.contains("image/jpeg"))
        assertTrue(screen.contains("image/png"))
        assertTrue(screen.contains("image/webp"))
        assertTrue(client.contains("withContext(Dispatchers.IO)"))
        assertTrue(client.contains("setChunkedStreamingMode(8192)"))
        assertFalse(screen.contains("/preview/pos/payments/package"))
    }

    @Test
    fun settingsContentClearsRaisedBottomNavigation() {
        val hub = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsHubScreen.kt")
        val screens = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsManagementScreens.kt")
        assertTrue(hub.contains("bottom = 156.dp"))
        assertTrue(screens.contains("bottom = 156.dp"))
    }
}
