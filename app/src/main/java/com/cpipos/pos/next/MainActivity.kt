package com.cpipos.pos.next

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.ui.MobilePosLiveScreen

class MainActivity : ComponentActivity() {
    // Android uses the existing public HTTPS POS API; privileged credentials remain server-side.
    private val webPosClient by lazy { WebPosClient(BuildConfig.WEB_POS_API_URL, applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Display the existing CpIPOS icon in Android's native launch window.
        // The brief minimum visibility applies only to a fresh Activity launch.
        // It does not wait for network, change the authenticated session, or
        // touch the sale journal/payment workflow.
        val splashStart = SystemClock.uptimeMillis()
        val splash = installSplashScreen()
        val minimumEnd = if (savedInstanceState == null) splashStart + 900L else splashStart
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() < minimumEnd }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { provider.remove() }
                .start()
        }
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
