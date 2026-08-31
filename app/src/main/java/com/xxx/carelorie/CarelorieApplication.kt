package com.xxx.carelorie

import android.app.Application
import com.xxx.carelorie.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CarelorieApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Outlives every screen, so a queued upload is not cancelled by navigating around. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // If the user didn't want to be remembered, clear the ID on start
        // so they have to login again.
        if (!container.sessionManager.isRememberMe()) {
            container.sessionManager.clearSession()
        }

        flushPendingUploads()
    }

    /**
     * Sends anything written while offline as soon as the app opens.
     *
     * Uploads used to happen only when a screen that syncs was visited, so an entry logged on a
     * plane sat on the device until the user happened to open the food log again. Draining at
     * start closes most of that window.
     *
     * This is not a substitute for a real background sync — WorkManager, which can run without
     * the app being opened at all and retry with backoff, is the proper version. It fails quietly
     * on purpose: there is nothing useful to tell someone at launch about a retry that can simply
     * happen again later.
     */
    private fun flushPendingUploads() {
        val userId = container.sessionManager.getUserId()
        if (userId.isEmpty()) return
        appScope.launch {
            runCatching { container.foodRepository.flushOutbox() }
            runCatching { container.mealPresetRepository.refresh(userId) }
        }
    }
}
