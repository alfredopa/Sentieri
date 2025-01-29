package com.apstudio.sentieri.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Sentieri::class, Track::class, PoiDB::class, FotoPoi::class], version = 1)
abstract class SentieriDB : RoomDatabase() {
    abstract val sentieriDao: SentieriDao
    abstract val trackDao: TrackDao
    abstract val poiDao: PoiDao
    abstract val fotoPoiDao: FotoPoiDao


    companion object {

        @Volatile
        private var INSTANCE: SentieriDB? = null
        fun getInstance(context: Context): SentieriDB {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        SentieriDB::class.java,
                        "sentieri.db"
                    )
                        .createFromAsset("sentieri.db")
                        //.addMigrations(MIGRATION_1_2)
                        .build()
                }
                return instance
            }
        }

        /*private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Aggiunta dei campi alla tabella SentieriB
                db.execSQL("ALTER TABLE Sentieri ADD COLUMN DataFine TEXT")
                db.execSQL("ALTER TABLE Sentieri ADD COLUMN TempoTot REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Sentieri ADD COLUMN TempoInMov REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Sentieri ADD COLUMN MediaVel REAL NOT NULL DEFAULT 0")
            }
        }*/
    }
}