package com.cpipos.pos.next.core.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cpipos.pos.next.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks/releases stable Native Mobile versions, downloads in the background, verifies
 * exact SHA-256 + APK package/version + signing certificate, then requests Android's
 * system installer. Ordinary sideloaded applications CANNOT silently install updates.
 * No update failure may interrupt a live transaction, payment or offline journal.
 */
internal object NativeApkUpdateManager {
    private const val PREFS = "native_stable_update"
    private const val APK_NAME = "CpIPOS-Native-Mobile-verified-update.apk"
    private const val EXPECTED_CERT =
        "6be0a9aef346a5b47162c8928c5018a01d0e7d81b4eb177bf2fb89922dc2a27a"
    private const val CHECK_PATH = "/api/android-pos/native-update-policy"
    private val _state = MutableStateFlow(NativeApkUpdateState())
    private val checkMutex = Mutex()
    val state = _state.asStateFlow()

    private data class Offer(
        val versionCode: Long,
        val versionName: String,
        val sha256: String,
        val url: String
    )

    internal fun validOffer(json: JSONObject, installedCode: Long): Boolean {
        if (!json.optBoolean("ok") || !json.optBoolean("updateAvailable")) return false
        val url = json.optString("apkUrl")
        val sha = json.optString("apkSha256")
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val name = json.optString("versionName")
        val code = json.optLong("versionCode", 0)
        val expectedPath = "/sstdevelopaminno/CpIPOS/releases/download/" +
            "native-mobile-v" + name + "/CpIPOS-Native-Mobile-" + name + ".apk"
        return code > installedCode &&
            name.matches(Regex("\\d+\\.\\d+\\.\\d+")) &&
            uri.scheme == "https" && uri.host == "github.com" && uri.rawPath == expectedPath && uri.rawQuery == null && uri.rawFragment == null &&
            sha.matches(Regex("[0-9a-f]{64}")) &&
            json.optString("signingCertSha256").lowercase() == EXPECTED_CERT &&
            json.optString("packageName") == "com.cpipos.pos.next"
    }

    private fun apkFile(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?.let { File(it, APK_NAME) }

    private fun downloadManager(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun checkAndStart(context: Context) = checkMutex.withLock {
        withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG || context.packageName != "com.cpipos.pos.next") return@withContext
        val appContext = context.applicationContext
        runCatching {
            val current = if (Build.VERSION.SDK_INT >= 28) {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                val legacy = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()
                legacy
            }
            val base = BuildConfig.WEB_POS_API_URL.trimEnd('/')
            val endpoint = URL(base + CHECK_PATH + "?version_code=" + current)
            require(endpoint.protocol == "https")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode != 200) return@runCatching
                val payload = connection.inputStream.bufferedReader().use { reader ->
                    JSONObject(reader.readText().take(12000))
                }
                if (!validOffer(payload, current)) return@runCatching
                val offer = Offer(
                    versionCode = payload.getLong("versionCode"),
                    versionName = payload.getString("versionName"),
                    sha256 = payload.getString("apkSha256"),
                    url = payload.getString("apkUrl")
                )
                val target = apkFile(appContext) ?: return@runCatching
                val old = preferences(appContext)
                if (target.isFile && verifyApk(appContext, target, offer)) {
                    _state.value = NativeApkUpdateState("ready", offer.versionName)
                    return@runCatching
                }
                val previous = old.getLong("download_id", -1L)
                if (previous > 0 && old.getString("sha256", "") == offer.sha256 &&
                    old.getLong("version_code", 0L) == offer.versionCode) {
                    _state.value = NativeApkUpdateState("downloading", offer.versionName)
                    return@runCatching
                }
                if (previous > 0) runCatching { downloadManager(appContext).remove(previous) }
                if (target.exists()) target.delete()
                val req = DownloadManager.Request(Uri.parse(offer.url))
                    .setTitle("อัปเดต CpiPOS Mobile " + offer.versionName)
                    .setDescription("ดาวน์โหลด APK ที่ตรวจสอบลายเซ็นแล้วก่อนติดตั้ง")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
                val id = downloadManager(appContext).enqueue(req)
                old.edit()
                    .putLong("download_id", id)
                    .putLong("version_code", offer.versionCode)
                    .putString("version_name", offer.versionName)
                    .putString("sha256", offer.sha256)
                    .putString("url", offer.url)
                    .apply()
                _state.value = NativeApkUpdateState("downloading", offer.versionName)
            } finally {
                connection.disconnect()
            }
        }.onFailure { _state.value = NativeApkUpdateState("idle") }
        }
    }

    /** Poll only while in app foreground; DownloadManager continues after process death. */
    suspend fun poll(context: Context) = withContext(Dispatchers.IO) {
        if (_state.value.phase != "downloading") return@withContext
        val appContext = context.applicationContext
        val p = preferences(appContext)
        val id = p.getLong("download_id", -1)
        if (id < 0) {
            _state.value = NativeApkUpdateState("idle")
            return@withContext
        }
        val cursor = runCatching {
            downloadManager(appContext).query(DownloadManager.Query().setFilterById(id))
        }.getOrNull() ?: return@withContext
        cursor.use {
            if (!it.moveToFirst()) {
                clear(appContext)
                return@withContext
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = apkFile(appContext)
                    val offer = Offer(
                        versionCode = p.getLong("version_code", 0L),
                        versionName = p.getString("version_name", "").orEmpty(),
                        sha256 = p.getString("sha256", "").orEmpty(),
                        url = p.getString("url", "").orEmpty()
                    )
                    if (file != null && verifyApk(appContext, file, offer)) {
                        _state.value = NativeApkUpdateState("ready", offer.versionName)
                    } else {
                        clear(appContext)
                        file?.delete()
                        _state.value = NativeApkUpdateState("idle")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    clear(appContext)
                    _state.value = NativeApkUpdateState("idle")
                }
            }
        }
    }

    private fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun certificateHashes(context: Context, path: String): Set<String> {
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val info = if (path == context.packageName) {
            manager.getPackageInfo(path, flags)
        } else {
            manager.getPackageArchiveInfo(path, flags)
        } ?: return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val legacy = info.signatures.orEmpty()
            legacy
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.toSet()
    }

    private fun verifyApk(context: Context, apk: File, offer: Offer): Boolean = runCatching {
        if (!apk.isFile || apk.length() < 1024 * 1024 || hash(apk) != offer.sha256) return@runCatching false
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return@runCatching false
        if (info.packageName != "com.cpipos.pos.next") return@runCatching false
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else {
                @Suppress("DEPRECATION")
                val legacy = info.versionCode.toLong()
                legacy
            }
        if (archiveCode != offer.versionCode) return@runCatching false
        val apkCerts = certificateHashes(context, apk.absolutePath)
        val installedCerts = certificateHashes(context, context.packageName)
        apkCerts.isNotEmpty() && apkCerts == installedCerts && EXPECTED_CERT in apkCerts
    }.getOrDefault(false)

    fun openSystemInstaller(context: Context) {
        val p = preferences(context)
        val file = apkFile(context) ?: return
        val offer = Offer(
            p.getLong("version_code", 0L), p.getString("version_name", "").orEmpty(),
            p.getString("sha256", "").orEmpty(), p.getString("url", "").orEmpty()
        )
        if (_state.value.phase != "ready" || !verifyApk(context, file, offer)) {
            _state.value = NativeApkUpdateState("idle")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val request = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName))
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(request) }
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".nativefileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            _state.value = NativeApkUpdateState("idle")
        }
    }
}

internal data class NativeApkUpdateState(
    val phase: String = "idle",
    val versionName: String = ""
)
