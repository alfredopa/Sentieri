package com.apstudio.sentieri

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.apstudio.sentieri.db.SentieriRepo

class SentieriFactory(
    private val repository: SentieriRepo,
    private val application: Application
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if(modelClass.isAssignableFrom(SentieriViewModel::class.java)){
            return SentieriViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown View Model class")
    }
}