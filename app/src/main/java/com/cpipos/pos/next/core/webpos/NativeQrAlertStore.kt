package com.cpipos.pos.next.core.webpos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Local POS register alert inbox: tracks dismissed event IDs, outstanding popups,
 * service ACKs and a conservative polling cursor. No customer PII, PINs or sales data.
 * All keys are tenant/branch/device-scoped to prevent alert leakage across logins.
 */
class NativeQrAlertStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_qr_alerts_v1", Context.MODE_PRIVATE
    )
    private fun key(session: WebPosSession): String =
        "${session.tenantId}:${session.branchId}:${session.deviceCode}"

    fun cursor(session: WebPosSession): String? =
        prefs.getString(key(session) + ":cursor", null)

    fun setCursor(session: WebPosSession, cursor: String) {
        require(runCatching { Instant.parse(cursor) }.isSuccess)
        check(prefs.edit().putString(key(session) + ":cursor", cursor).commit())
    }

    fun readSeen(session: WebPosSession): Set<String> {
        val data = prefs.getString(key(session) + ":seen", null) ?: return emptySet()
        val arr = runCatching { JSONArray(data) }.getOrNull() ?: return emptySet()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            .toSet()
    }

    fun markSeen(session: WebPosSession, eventId: String) {
        val current = readSeen(session).toMutableList().apply { remove(eventId); add(eventId) }
        val limited = current.takeLast(300)
        check(prefs.edit().putString(key(session) + ":seen", JSONArray(limited).toString()).commit())
    }

    fun readPending(session: WebPosSession): List<WebQrActivityEvent> {
        val data = prefs.getString(key(session) + ":pending", null) ?: return emptyList()
        val arr = runCatching { JSONArray(data) }.getOrNull() ?: return emptyList()
        val cutoff = Instant.now().minusSeconds(24 * 3600).toEpochMilli()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val whenAt = runCatching { Instant.parse(o.optString("created")).toEpochMilli() }
                .getOrNull() ?: return@mapNotNull null
            if (whenAt < cutoff) return@mapNotNull null
            val id = o.optString("id")
            val tableId = o.optString("table")
            if (id.isBlank() || tableId.isBlank()) return@mapNotNull null
            WebQrActivityEvent(
                id = id, tableId = tableId, tableCode = o.optString("code"),
                type = o.optString("type"), itemCount = o.optInt("qty", 0),
                subtotal = null, createdAt = o.optString("created")
            )
        }.takeLast(50)
    }

    fun savePending(session: WebPosSession, events: List<WebQrActivityEvent>) {
        val rows = JSONArray()
        for (event in events.takeLast(50)) {
            rows.put(JSONObject().put("id", event.id).put("table", event.tableId)
                .put("code", event.tableCode).put("type", event.type)
                .put("qty", event.itemCount).put("created", event.createdAt))
        }
        check(prefs.edit().putString(key(session) + ":pending", rows.toString()).commit())
    }

    fun readAcks(session: WebPosSession): Map<String, Boolean> {
        val obj = runCatching { JSONObject(prefs.getString(key(session) + ":acks", "{}")!!) }
            .getOrDefault(JSONObject())
        return obj.keys().asSequence().map { it to obj.optBoolean(it, false) }.toMap()
    }

    fun queueAck(session: WebPosSession, eventId: String, goToTable: Boolean) {
        val obj = JSONObject(prefs.getString(key(session) + ":acks", "{}") ?: "{}")
        obj.put(eventId, goToTable)
        check(prefs.edit().putString(key(session) + ":acks", obj.toString()).commit())
    }

    fun confirmedAck(session: WebPosSession, eventId: String) {
        val obj = JSONObject(prefs.getString(key(session) + ":acks", "{}") ?: "{}")
        obj.remove(eventId)
        check(prefs.edit().putString(key(session) + ":acks", obj.toString()).commit())
    }
}
