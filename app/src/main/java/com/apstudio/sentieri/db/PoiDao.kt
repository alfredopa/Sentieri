package com.apstudio.sentieri.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PoiDao {
    @Query("SELECT * from PoiDB ")
    fun livePoiDB(): LiveData<List<PoiDB>>

    @Query("SELECT * from PoiDB")
    suspend fun listPoiDB(): List<PoiDB>

    @Query("SELECT * from PoiDB WHERE TrackId = :id")
    // restituisce tutti i POI della traccia ID
    fun getPoibyID(id: Int): List<PoiDB>

    @Insert
    suspend fun insertDB(item: PoiDB) : Long

    @Query("SELECT uuid FROM PoiDB")
    suspend fun getAllUuids(): List<String>

    @Query("SELECT * FROM PoiDB WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): PoiDB?

    @Upsert
    suspend fun upsert(item: PoiDB): Long
}