package com.xxx.carelorie

import android.app.Application
import com.xxx.carelorie.data.AppContainer

class CarelorieApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
