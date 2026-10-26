package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TableOrderQrContractTest {
    private val table = "da11e2aa-87bc-475d-919b-e0e22cbd7832"
    private val qr = "bd3e9c9a-f7bb-4520-bf93-52c340e7fc26"
    private val session = "ae73b4f1-c8e0-4e72-9074-01766c520412"
    private val host = "https://cp-ipos-web.vercel.app"

    private fun response(): JSONObject = JSONObject()
        .put("table_id", table)
        .put("table_code", "T01")
        .put("table_name", "โต๊ะ 1")
        .put("qr_session_id", qr)
        .put("table_session_id", session)
        .put("order_url", "$host/table-order/signed-token")
        .put("qr_data_url", "data:image/png;base64,iVBORw0KGgo=")
        .put("expires_at", "2099-01-01T00:00:00Z")
        .put("expiry_mode", "bill")

    @Test fun parsesServerIssuedTableQrForTrustedBranch() {
        val parsed = parseTableOrderQr(response(), table, host)
        assertEquals(table, parsed.tableId)
        assertEquals(session, parsed.tableSessionId)
        assertEquals("T01", parsed.tableCode)
        assertEquals("$host/table-order/signed-token", parsed.orderUrl)
        assertEquals("iVBORw0KGgo=", parsed.qrPngBase64)
    }

    @Test fun refusesQrForOtherTable() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTableOrderQr(response(), "56c61e5f-b32f-4c3f-9730-566a93c81277", host)
        }
    }

    @Test fun refusesCrossHostQr() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTableOrderQr(response().put("order_url", "https://fake-site.example/table-order/abc"),
                table, host)
        }
    }

    @Test fun refusesHttpQrAndInvalidImage() {
        assertThrows(IllegalArgumentException::class.java) {
            parseTableOrderQr(response().put("order_url",
                "http://cp-ipos-web.vercel.app/table-order/x"), table, host)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTableOrderQr(response().put("qr_data_url", "invalid"), table, host)
        }
    }
}
