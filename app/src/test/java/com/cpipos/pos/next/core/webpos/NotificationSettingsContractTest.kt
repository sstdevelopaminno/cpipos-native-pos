package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsContractTest {
    @Test
    fun parsesSharedCpiposNotificationSettings() {
        val parsed = parsePosNotificationSettings(
            JSONObject().put("notification_settings", JSONObject()
                .put("table_qr_popup_enabled", false)
                .put("table_qr_sound_enabled", true)
                .put("table_qr_sound_volume", 1.0))
        )
        assertFalse(parsed.popupEnabled)
        assertTrue(parsed.soundEnabled)
        assertEquals(1.0f, parsed.soundVolume, 0.001f)
    }

    @Test
    fun defaultsRemainOperationalWhenFieldsAreMissing() {
        val parsed = parsePosNotificationSettings(JSONObject())
        assertTrue(parsed.popupEnabled)
        assertTrue(parsed.soundEnabled)
        assertEquals(0.8f, parsed.soundVolume, 0.001f)
    }
}
