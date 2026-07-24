package com.apstudio.sentieri

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AppSentieri : Application(), ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore by lazy { ViewModelStore() }

}