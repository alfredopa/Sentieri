package com.apstudio.sentieri.db

import android.content.Context
import androidx.activity.result.launch
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow

class SentieriRepo(context: Context) {

    val database: SentieriDB = SentieriDB.getInstance(context)
    val sentieriDao: SentieriDao = SentieriDB.getInstance(context).sentieriDao
    val poiDao: PoiDao = SentieriDB.getInstance(context).poiDao
    val fotoPoiDao: FotoPoiDao = SentieriDB.getInstance(context).fotoPoiDao
    val sentieriDB = sentieriDao.getItems()
    val trackDao = database.trackDao

    suspend fun insertDB(sentieriDB: Sentieri): Long {
        return sentieriDao.insertDB(sentieriDB)
    }

    fun cercaId(id: Int): LiveData<Sentieri> {
        return sentieriDao.getItem(id).asLiveData()
    }

    suspend fun updateDB(sentieriDB: Sentieri): Int {
        return sentieriDao.updateDB(sentieriDB)
    }

    suspend fun deleteDB(sentieriDB: Sentieri): Int {
        return sentieriDao.deleteDB(sentieriDB)
    }

    suspend fun deleteSentiero(id: Int): Int {
        return sentieriDao.deleteSentiero(id)
    }

    fun cercaNome(searchQuery: String): Flow<List<Sentieri>> {
        return sentieriDao.cercaNome(searchQuery)
    }

    fun cercaPoi(id: Int): List<PoiDB> {
        return poiDao.getPoibyID(id)
    }

    fun listaFotoId(id: Int): List<FotoPoi> {
        return fotoPoiDao.getFotoPoibyID(id)
    }

    suspend fun cancellaSentiero(id: Int) {
        // non riesco ad attivare la transazione
        //database.runInTransaction{
        trackDao.deleteTrack(id)
        sentieriDao.deleteSentiero(id)
        //}
    }

}