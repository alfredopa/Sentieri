package com.apstudio.sentieri

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AppSentieri() : Application(), ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        SimpleFileLogger.initialize(this)
        SimpleFileLogger.log("MyApplication", "App avviata, log manuale.")
    }
}