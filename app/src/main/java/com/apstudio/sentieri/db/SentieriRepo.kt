package com.apstudio.sentieri.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow

class SentieriRepo(context: Context) {

    val dao : SentieriDao = SentieriDB.getInstance(context).sentieriDao
    val poiDao : PoiDao = SentieriDB.getInstance(context).poiDao
    val fotoPoiDao : FotoPoiDao = SentieriDB.getInstance(context).fotoPoiDao
    val sentieriDB = dao.getItems()

    suspend fun insertDB(sentieriDB: Sentieri): Long {
        return dao.insertDB(sentieriDB)
    }

    fun CercaId(id : Int): LiveData<Sentieri> {
        return  dao.getItem(id).asLiveData()
    }

    suspend fun updateDB(sentieriDB: Sentieri): Int {
        return dao.updateDB(sentieriDB)
    }

    suspend fun deleteDB(sentieriDB: Sentieri): Int {
        return dao.deleteDB(sentieriDB)
    }

    suspend fun deleteAll(): Int {
        return dao.deleteAll()
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


}