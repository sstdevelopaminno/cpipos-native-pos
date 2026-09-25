package com.cpipos.pos.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.ui.MobilePosLiveScreen

class MainActivity : ComponentActivity() {
    // Android uses the existing public HTTPS POS API; privileged credentials remain server-side.
    private val webPosClient by lazy { WebPosClient(BuildConfig.WEB_POS_API_URL) }

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
