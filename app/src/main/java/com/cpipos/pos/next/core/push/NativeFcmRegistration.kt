package com.cpipos.pos.next.core.push

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import com.cpipos.pos.next.BuildConfig
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Android Firebase config is OPTIONAL; when absent the existing POS works
 * without FCM. No server credentials belong in Android. Tokens are registered
 * only after verified POS sign-in; installation re-binding revokes old branch.
 */
object NativeFcmRegistration {
    private const val PREFS = "cpipos_native_fcm_v1"
    val navigationRevision = mutableIntStateOf(0)
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun installationId(context: Context): String {
        val stored = prefs(context).getString("installation_id", null)
        if (!stored.isNullOrBlank()) return stored
        return UUID.randomUUID().toString().also {
            check(prefs(context).edit().putString("installation_id", it).commit())
        }
    }

    fun hasFirebase(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    suspend fun register(context: Context, client: WebPosClient, session: WebPosSession): Boolean {
        if (!hasFirebase(context)) return false
        val fcmToken = suspendCancellableCoroutine<String> { cont ->
            val task = FirebaseMessaging.getInstance().token
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            task.addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
        client.registerNativeQrPush(session, installationId(context), fcmToken, BuildConfig.APPLICATION_ID)
        check(prefs(context).edit()
            .putString("bound_tenant", session.tenantId)
            .putString("bound_branch", session.branchId)
            .putString("current_token", fcmToken).commit())
        return true
    }

    suspend fun unregister(context: Context, client: WebPosClient, session: WebPosSession) {
        if (!hasFirebase(context)) return
        try {
            client.unregisterNativeQrPush(session, installationId(context))
        } finally {
            clearScope(context)
        }
    }

    fun saveLatestToken(context: Context, token: String) {
        prefs(context).edit().putString("current_token", token).apply()
    }

    fun sameBoundBranch(context: Context, tenant: String, branch: String): Boolean {
        val p = prefs(context)
        return p.getString("bound_tenant", null) == tenant &&
            p.getString("bound_branch", null) == branch
    }

    fun clearScope(context: Context) {
        prefs(context).edit().remove("bound_tenant").remove("bound_branch").apply()
    }

    fun markReceived(context: Context, eventId: String): Boolean {
        val p = prefs(context)
        val raw = p.getString("shown_ids", "") ?: ""
        val ids = raw.split(",").filter { it.isNotBlank() }
        if (eventId in ids) return false
        return p.edit().putString("shown_ids", (ids + eventId).takeLast(200)
            .joinToString(",")).commit()
    }

    fun captureNotificationIntent(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("cpipos_qr_table_id") ?: return
        val tenant = intent.getStringExtra("cpipos_qr_tenant_id") ?: return
        val branch = intent.getStringExtra("cpipos_qr_branch_id") ?: return
        if (runCatching { UUID.fromString(id) }.isFailure ||
            !sameBoundBranch(context, tenant, branch)) return
        if (prefs(context).edit().putString("pending_table_id", id).commit()) {
            navigationRevision.intValue += 1
        }
    }

    fun pendingTable(context: Context, session: WebPosSession): String? {
        if (!sameBoundBranch(context, session.tenantId, session.branchId)) return null
        return prefs(context).getString("pending_table_id", null)
    }

    fun clearPendingTable(context: Context) {
        prefs(context).edit().remove("pending_table_id").apply()
    }
}
