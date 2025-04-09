package com.apstudio.sentieri.db

import android.content.Context
import androidx.activity.result.launch
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow

class SentieriRepo(context: Context) {

    val database : SentieriDB = SentieriDB.getInstance(context)
    val dao : SentieriDao = SentieriDB.getInstance(context).sentieriDao
    val poiDao : PoiDao = SentieriDB.getInstance(context).poiDao
    val fotoPoiDao : FotoPoiDao = SentieriDB.getInstance(context).fotoPoiDao
    val sentieriDB = dao.getItems()

    suspend fun insertDB(sentieriDB: Sentieri): Long {
        return dao.insertDB(sentieriDB)
    }

    fun cercaId(id : Int): LiveData<Sentieri> {
        return  dao.getItem(id).asLiveData()
    }

    suspend fun updateDB(sentieriDB: Sentieri): Int {
        return dao.updateDB(sentieriDB)
    }

    suspend fun deleteDB(sentieriDB: Sentieri): Int {
        return dao.deleteDB(sentieriDB)
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
            val trackDao = database.trackDao
            val sentieriDao = database.sentieriDao
                trackDao.deleteTrack(id)
                sentieriDao.deleteSentiero(id)
        //}
    }

}