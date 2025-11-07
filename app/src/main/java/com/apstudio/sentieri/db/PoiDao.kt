package com.apstudio.sentieri.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PoiDao {
    @Query("SELECT * from PoiDB ")
    fun livePoiDB(): LiveData<List<PoiDB>>

    @Query("SELECT * from PoiDB")
    fun listPoiDB(): List<PoiDB>

    @Query("SELECT * from PoiDB WHERE TrackId = :id")
    // restituisce tutti i POI della traccia ID
    fun getPoibyID(id: Int): List<PoiDB>

    @Insert
    suspend fun insertDB(item: PoiDB) : Long
}