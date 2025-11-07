package com.apstudio.sentieri.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FotoPoiDao {

        @Query("SELECT * from FotoPoi ")
        fun liveFotoPoiDB(): LiveData<List<FotoPoi>>

        @Query("SELECT * from FotoPoi")
        fun listFotoPoiDB(): List<FotoPoi>

        @Query("SELECT * from FotoPoi WHERE TrackId = :id")
        // restituisce tutti i POI della traccia ID
        fun getFotoPoibyID(id: Int): List<FotoPoi>

        @Insert
        suspend fun insertDB(item: FotoPoi) : Long
}
