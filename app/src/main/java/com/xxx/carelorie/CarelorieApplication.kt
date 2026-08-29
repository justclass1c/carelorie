package com.xxx.carelorie

import android.app.Application
import com.xxx.carelorie.data.AppContainer

class CarelorieApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // If the user didn't want to be remembered, clear the ID on start
        // so they have to login again.
        if (!container.sessionManager.isRememberMe()) {
            container.sessionManager.clearSession()
        }
    }
}
