package com.cpipos.pos.next.core.update

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeApkUpdatePolicyTest {
    private fun offer(name: String = "2.1.0", code: Long = 2010000) = JSONObject()
        .put("ok", true).put("updateAvailable", true)
        .put("versionName", name).put("versionCode", code)
        .put("apkUrl", "https://github.com/sstdevelopaminno/CpIPOS/releases/download/native-mobile-v$name/CpIPOS-Native-Mobile-$name.apk")
        .put("apkSha256", "a".repeat(64))
        .put("signingCertSha256", "6be0a9aef346a5b47162c8928c5018a01d0e7d81b4eb177bf2fb89922dc2a27a")
        .put("packageName", "com.cpipos.pos.next")

    @Test fun acceptsOnlyNewerStableNativeSignedOffer() {
        assertTrue(NativeApkUpdateManager.validOffer(offer(), 2000000))
        assertFalse(NativeApkUpdateManager.validOffer(offer(), 2010000))
        assertFalse(NativeApkUpdateManager.validOffer(offer("2.1.0-rc1"), 2000000))
    }

    @Test fun refusesWrongCertificatePackageOrOffsiteDownload() {
        assertFalse(NativeApkUpdateManager.validOffer(offer().put("signingCertSha256", "0".repeat(64)), 2000000))
        assertFalse(NativeApkUpdateManager.validOffer(offer().put("packageName", "com.example.fake"), 2000000))
        assertFalse(NativeApkUpdateManager.validOffer(offer().put("apkUrl", "https://evil.example/update.apk"), 2000000))
        assertFalse(NativeApkUpdateManager.validOffer(offer().put("apkSha256", "mismatched"), 2000000))
    }
}
