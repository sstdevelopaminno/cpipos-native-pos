package com.cpipos.pos.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cpipos.pos.next.core.webpos.NativeAndroidPairing
import com.cpipos.pos.next.core.webpos.NativeInstallIdentity
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.ui.MobilePosLiveScreen

class MainActivity : ComponentActivity() {
    // Fully native Kotlin/Compose UI, NOT a WebView.
    // The shared HTTPS POS API connects mobile and tablet to the same CpiPOS-001
    // tenant/branch data; privileged database credentials remain server-side.
    private val webPosClient by lazy {
        val pairing = if (BuildConfig.ANDROID_PAIRING_ENABLED) {
            NativeAndroidPairing(
                installId = NativeInstallIdentity.getOrCreate(this),
                appVersion = BuildConfig.VERSION_NAME.take(40)
            )
        } else null
        WebPosClient(BuildConfig.WEB_POS_API_URL, pairing)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // One UI and one real auth/sales path; no second demo login or demo button.
                    MobilePosLiveScreen(client = webPosClient)
                }
            }
        }
    }
}
