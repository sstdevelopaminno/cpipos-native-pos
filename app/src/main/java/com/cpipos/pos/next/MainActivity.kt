package com.cpipos.pos.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cpipos.pos.next.auth.GuardedNativeAuthGateway
import com.cpipos.pos.next.core.supabase.SupabaseClientProvider
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.ui.MobilePosLiveScreen
import com.cpipos.pos.next.ui.MobilePosPreviewScreen

class MainActivity : ComponentActivity() {
    private val previewGateway = GuardedNativeAuthGateway()
    // Only public HTTPS POS API endpoint; server holds administrative database keys.
    private val webPosClient by lazy { WebPosClient(BuildConfig.WEB_POS_API_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var demo by remember { mutableStateOf(false) }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (demo) {
                        MobilePosPreviewScreen(
                            isSupabaseConfigured = SupabaseClientProvider.isConfigured,
                            gateway = previewGateway
                        )
                    } else {
                        MobilePosLiveScreen(
                            client = webPosClient,
                            onPreview = { demo = true }
                        )
                    }
                }
            }
        }
    }
}
