package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableQrActivityContractTest {
    private val tableId = "b157f2bb-f382-4e22-85d1-4cf77ed23245"
    private val orderId = "9b4116c7-83d1-4f70-97c0-1c24bb1de678"
    private val serviceId = "c2b1bf3a-c257-48bd-838f-b08126410bd2"

    @Test fun parsesAllThreeOperationalTypesFromCanonicalBackend() {
        val events = JSONArray()
            .put(JSONObject().put("id", orderId).put("table_id", tableId)
                .put("table", JSONObject().put("table_code", "A01"))
                .put("event_type", "order").put("item_count", 3).put("subtotal", 120.50))
            .put(JSONObject().put("id", serviceId).put("table_id", tableId)
                .put("table", JSONObject().put("table_code", "A01"))
                .put("event_type", "call_staff"))
            .put(JSONObject().put("id", "334e9c92-c0c0-4487-8432-795d7bb951da")
                .put("table_id", tableId).put("event_type", "request_checkout"))
        val since = "2026-10-25T00:00:00Z"
        val result = parseQrActivityPage(
            JSONObject().put("events", events).put("cursor", "2026-10-25T00:00:02Z")
                .put("server_time", "2026-10-25T00:00:03Z"),
            since
        )
        assertEquals(listOf("order", "call_staff", "request_checkout"),
            result.events.map { it.type })
        assertEquals("A01", result.events.first().tableCode)
        assertEquals(3, result.events.first().itemCount)
        assertEquals(Satang.fromBaht("120.50"), result.events.first().subtotal)
        assertNull(result.events[1].subtotal)
        assertEquals("2026-10-25T00:00:02Z", result.cursor)
    }

    @Test fun rejectsInvalidOrUnknownEventTypes() {
        val data = JSONObject().put("events", JSONArray()
            .put(JSONObject().put("id", "bad-id").put("table_id", tableId)
                .put("event_type", "order"))
            .put(JSONObject().put("id", serviceId).put("table_id", tableId)
                .put("event_type", "untrusted_type")))
        assertEquals(0, parseQrActivityPage(data, "2026-10-25T00:00:00Z").events.size)
    }
}
