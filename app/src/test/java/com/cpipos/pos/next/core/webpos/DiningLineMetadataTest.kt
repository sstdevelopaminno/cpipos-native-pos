package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiningLineMetadataTest {
    @Test fun plainLinesWithCentralPosBookkeepingAreEditable() {
        assertFalse(hasDiningCustomMetadata(null))
        assertFalse(hasDiningCustomMetadata(JSONObject()))
        assertFalse(hasDiningCustomMetadata(JSONObject("""{"source":"pos_sales_mvp"}""")))
        assertFalse(hasDiningCustomMetadata(JSONObject("""{"source":"pos_sales","pos_edit_updated_at":"2026-11-08"}""")))
    }

    @Test fun customOptionsAndCancellationMarkersStillBlockLegacyReplacement() {
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"native_selection":{"price_kind":"special"}}""")))
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"modifier_option_ids":["a"]}""")))
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"notes":"not a plain line"}""")))
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"bill_line_state":"cancelled"}""")))
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"cancelled_quantity":1}""")))
        assertTrue(hasDiningCustomMetadata(JSONObject("""{"source":"pos_sales_mvp","unexpected_field":"future"}""")))
    }
}
