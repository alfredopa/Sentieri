package com.apstudio.sentieri.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * from Track ")
    fun getPoint(): Flow<List<Track>>

    @Query("SELECT * from Track")
    fun getTutti(): List<Track>

    @Query("SELECT * from Track WHERE TrackId = :id")
    // restituisce tutti i punti della traccia ID
    fun getTraccia(id: Int): List<Track>

    @Query("SELECT Ele from Track where TrackId = :id")
    // restituisce tutte le elevazioni della traccia id
    suspend fun getElev(id: Int): List<Float>

    @Insert
    suspend fun insertDB(item: Track) : Long
}