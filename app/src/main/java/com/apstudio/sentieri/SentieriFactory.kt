package com.apstudio.sentieri

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.apstudio.sentieri.db.SentieriRepo

class SentieriFactory(
    private val repository: SentieriRepo
    //private val  repositoryBaro: BaroRepo
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if(modelClass.isAssignableFrom(SentieriViewModel::class.java)){
            return SentieriViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown View Model class")
    }
}