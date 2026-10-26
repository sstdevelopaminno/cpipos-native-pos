package com.cpipos.pos.next.core.webpos

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/**
 * Crash-safe evidence for table transfers (server endpoint has no request id).
 * A source bill can be sent ONCE until its move outcome is confirmed.
 * Scope includes the actual table bill session, so new bills remain independent.
 */
internal class NativeTableMoveJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_table_move_journal_v1", Context.MODE_PRIVATE
    )
    data class Pending(val requestId: String, val targetId: String, val billSessionId: String)

    private fun key(s: WebPosSession, sourceId: String, billSessionId: String): String =
        listOf("table_move", s.tenantId, s.branchId, s.deviceCode, sourceId, billSessionId)
            .joinToString(":")

    @Synchronized fun pending(
        session: WebPosSession, sourceId: String, billSessionId: String
    ): Pending? {
        val raw = prefs.getString(key(session, sourceId, billSessionId), null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            Pending(
                obj.getString("request_id"),
                obj.getString("target_id"),
                obj.getString("table_session_id")
            )
        }.getOrElse {
            Pending("", "", billSessionId) // garbled journal blocks a second write
        }
    }

    @Synchronized fun prepare(
        session: WebPosSession, sourceId: String, targetId: String,
        billSessionId: String, requestId: String
    ) {
        listOf(sourceId, targetId, billSessionId, requestId).forEach {
            require(UUID.fromString(it).toString() == it)
        }
        require(sourceId != targetId)
        check(pending(session, sourceId, billSessionId) == null) {
            "table_move_outcome_unresolved"
        }
        val row = JSONObject()
            .put("request_id", requestId)
            .put("target_id", targetId)
            .put("table_session_id", billSessionId)
        check(prefs.edit().putString(
            key(session, sourceId, billSessionId), row.toString()
        ).commit()) { "table_move_journal_persist_failed" }
    }

    @Synchronized fun confirmed(
        session: WebPosSession, sourceId: String, billSessionId: String,
        requestId: String
    ) {
        check(pending(session, sourceId, billSessionId)?.requestId == requestId) {
            "table_move_journal_mismatch"
        }
        check(prefs.edit().remove(key(session, sourceId, billSessionId)).commit()) {
            "table_move_journal_clear_failed"
        }
    }
}
