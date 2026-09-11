package com.cpipos.pos.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cpipos.pos.next.auth.GuardedNativeAuthGateway
import com.cpipos.pos.next.core.supabase.SupabaseClientProvider
import com.cpipos.pos.next.ui.AuthPhase1Screen

class MainActivity : ComponentActivity() {
    private val authGateway = GuardedNativeAuthGateway()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthPhase1Screen(
                        isSupabaseConfigured = SupabaseClientProvider.isConfigured,
                        gateway = authGateway
                    )
                }
            }
        }
    }
}
