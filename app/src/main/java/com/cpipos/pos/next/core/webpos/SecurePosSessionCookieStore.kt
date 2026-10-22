package com.cpipos.pos.next.core.webpos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpCookie
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saves only the backend-issued opaque POS session cookie, not an employee code,
 * store credential, PIN, service key or Supabase token. Encrypted at rest with
 * an Android Keystore non-exportable AES key. The backend still validates every
 * request and enforces the device/session lease.
 */
internal class SecurePosSessionCookieStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("native_pos_session_v1", Context.MODE_PRIVATE)
    private val alias = "cpipos_native_session_aes_v1"
    private val cookieName = "pos_session_id"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun save(setCookieHeaders: List<String>) {
        for (header in setCookieHeaders) {
            val cookie = runCatching { HttpCookie.parse(header) }.getOrNull()
                ?.firstOrNull { it.name == cookieName } ?: continue
            val value = cookie.value.trim().trim('"')
            val maxAge = cookie.maxAge
            if (maxAge <= 0L || runCatching { UUID.fromString(value).toString() != value }.getOrDefault(true)) {
                clear()
                continue
            }
            val expiry = System.currentTimeMillis() + maxAge.coerceAtMost(72L * 3600L) * 1000L
            val record = JSONObject().put("value", value).put("expires_at", expiry).toString()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(record.toByteArray(Charsets.UTF_8))
            prefs.edit().putString("encrypted", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
        }
    }

    /** Return a Set-Cookie header with its original remaining TTL, or null. */
    fun restore(): String? = runCatching {
        val saved = prefs.getString("encrypted", null) ?: return null
        val parts = saved.split(':', limit = 2)
        if (parts.size != 2) { clear(); return null }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val record = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        val remaining = (record.getLong("expires_at") - System.currentTimeMillis()) / 1000L
        val value = record.getString("value")
        if (remaining <= 0L || UUID.fromString(value).toString() != value) {
            clear()
            return null
        }
        "$cookieName=$value; Path=/; Secure; HttpOnly; Max-Age=$remaining"
    }.getOrElse {
        clear()
        null
    }

    fun clear() {
        prefs.edit().remove("encrypted").apply()
    }
}
