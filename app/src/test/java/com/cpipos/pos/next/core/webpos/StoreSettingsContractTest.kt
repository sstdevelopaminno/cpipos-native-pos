package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreSettingsContractTest {
    @Test
    fun parsesSharedStoreReceiptProfile() {
        val parsed = parseStoreSettings(
            JSONObject().put(
                "store",
                JSONObject()
                    .put("id", "tenant-1")
                    .put("code", "STORE-01")
                    .put("name", "Legal Company")
                    .put("display_name", "Coffee House")
                    .put("logo_url", "data:image/webp;base64,AA==")
                    .put("company_address", "Bangkok")
                    .put("contact_phone", "020000000")
            )
        )
        assertEquals("tenant-1", parsed.id)
        assertEquals("STORE-01", parsed.code)
        assertEquals("Coffee House", parsed.displayName)
        assertEquals("data:image/webp;base64,AA==", parsed.logoUrl)
        assertEquals("Bangkok", parsed.companyAddress)
        assertEquals("020000000", parsed.contactPhone)
    }
}
