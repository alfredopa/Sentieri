package com.apstudio.sentieri.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FotoPoiDao {

        @Query("SELECT * from FotoPoi ")
        fun liveFotoPoiDB(): LiveData<List<FotoPoi>>

        @Query("SELECT * from FotoPoi")
        suspend fun listFotoPoiDB(): List<FotoPoi>

        @Query("SELECT * FROM FotoPoi WHERE trackUuid = :trackUuid")
        suspend fun getFotosByTrackUuid(trackUuid: String): List<FotoPoi>

        @Query("SELECT * from FotoPoi WHERE TrackId = :id")
        // restituisce tutti i POI della traccia ID
        fun getFotoPoibyID(id: Int): List<FotoPoi>

        @Insert
        suspend fun insertDB(item: FotoPoi) : Long

    @Query("SELECT uuid FROM FotoPoi")
    suspend fun getAllUuids(): List<String>

    @Query("SELECT * FROM FotoPoi WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): FotoPoi?

    @Upsert
    suspend fun upsert(item: FotoPoi): Long
}
