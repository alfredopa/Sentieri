package com.apstudio.sentieri.db

import android.content.Context
import androidx.activity.result.launch
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow

class SentieriRepo(
    private val dao: SentieriDao,
    private val poiDao: PoiDao,
    private val fotoPoiDao: FotoPoiDao,
    private val trackDao: TrackDao) {

    //val database: SentieriDB = SentieriDB.getInstance(context)
    //val sentieriDao: SentieriDao = SentieriDB.getInstance(context).sentieriDao
    //val poiDao: PoiDao = SentieriDB.getInstance(context).poiDao
    //val fotoPoiDao: FotoPoiDao = SentieriDB.getInstance(context).fotoPoiDao
    //val sentieriDB = sentieriDao.getItems()
    //val trackDao = database.trackDao
    val sentieriDB = dao.getItems() // Usa 'dao' invece di 'sentieriDao' (variabile locale che hai rimosso)

    suspend fun insertDB(item: Sentieri): Long { // Ho rinominato il parametro per chiarezza
        return dao.insertDB(item)
    }

    fun cercaId(id: Int): LiveData<Sentieri> {
        return dao.getItem(id).asLiveData()
    }

    suspend fun updateDB(item: Sentieri): Int {
        return dao.updateDB(item)
    }

    suspend fun deleteDB(item: Sentieri): Int {
        return dao.deleteDB(item)
    }

    suspend fun deleteSentiero(id: Int): Int {
        return dao.deleteSentiero(id)
    }

    fun cercaNome(searchQuery: String): Flow<List<Sentieri>> {
        return dao.cercaNome(searchQuery)
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
        dao.deleteSentiero(id)
        //}
    }

}