package com.apstudio.sentieri.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SentieriDao {

    @Query("SELECT * from sentieri ORDER BY dataOra DESC")
    fun getItems(): Flow<List<Sentieri>>

    @Query("SELECT * from sentieri WHERE id = :id")
    fun getItem(id: Int): Flow<Sentieri?>

    @Query("SELECT * from sentieri WHERE id = :id")
    suspend fun getItemSync(id: Int): Sentieri?

    // Specify the conflict strategy as IGNORE, when the user tries to add an
    // existing Item into the database Room ignores the conflict.
    //(onConflict = OnConflictStrategy.IGNORE)
    @Insert
    suspend fun insertDB(item: Sentieri) : Long

    @Update
    suspend fun updateDB(item: Sentieri) : Int

    @Delete
    suspend fun deleteDB(item: Sentieri) : Int

    @Query("DELETE from sentieri WHERE id = :id")
    suspend fun deleteSentiero(id: Int) : Int

    @Query("SELECT * FROM sentieri WHERE Nome LIKE :searchQuery")
    fun cercaNome(searchQuery: String): Flow<List<Sentieri>>

    @Query("SELECT max(id) from sentieri")
    suspend fun ultimoId() : Int
}