package com.cpipos.pos.next

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.cpipos.pos.next.core.push.NativeQrFirebaseService

class NativePosApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    var isForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        NativeQrFirebaseService.ensureChannel(this)
    }
    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        isForeground = startedActivities > 0
    }
    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        isForeground = startedActivities > 0
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
