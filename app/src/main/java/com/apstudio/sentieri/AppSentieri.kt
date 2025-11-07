package com.apstudio.sentieri

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.apstudio.sentieri.db.SentieriDao // Supponendo tu abbia un Dao
import com.apstudio.sentieri.db.SentieriDB // Supponendo tu abbia un Database
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.TrackDao

class AppSentieri : Application(), ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore by lazy { ViewModelStore() }

    // Istanzia il Database (che di solito è un singleton)
    private val database by lazy { SentieriDB.getInstance(this) }

    // Istanzia il DAO
    private val sentieriDao: SentieriDao by lazy { database.sentieriDao() } // Rinominato per chiarezza, SENZA ()
    private val poiDao: PoiDao by lazy { database.poiDao() }
    private val fotoPoiDao: FotoPoiDao by lazy { database.fotoPoiDao() }
    private val trackDao: TrackDao by lazy { database.trackDao() }
    // Istanzia il Repository (singleton a livello di app)
    val sentieriRepository: SentieriRepo by lazy {
        SentieriRepo(sentieriDao,
            poiDao,
            fotoPoiDao,
            trackDao) // Passa qui le dipendenze del Repository (es. il DAO)
    }

    // Istanzia la Factory (singleton a livello di app)
    val sentieriViewModelFactory: SentieriFactory by lazy {
        SentieriFactory(sentieriRepository)
    }

    // Istanzia il ViewModel (singleton a livello di app)
    val sentieriViewModel: SentieriViewModel by lazy {
        ViewModelProvider(this, sentieriViewModelFactory)[SentieriViewModel::class.java]
    }
    override fun onCreate() {
        super.onCreate()
        SimpleFileLogger.initialize(this)
        SimpleFileLogger.log("Sentieri", "App avviata, log manuale.")
    }
}