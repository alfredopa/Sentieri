package com.apstudio.sentieri.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Sentieri::class, Track::class, PoiDB::class, FotoPoi::class], version = 2, exportSchema = false)
abstract class SentieriDB : RoomDatabase() {
    abstract fun sentieriDao(): SentieriDao
    abstract fun trackDao(): TrackDao
    abstract fun poiDao(): PoiDao
    abstract fun fotoPoiDao(): FotoPoiDao


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
                        .addMigrations(MIGRATION_1_2)
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Recreate Sentieri table to fix nullability and remove extra columns (like LIVELLO)
                db.execSQL("CREATE TABLE IF NOT EXISTS `Sentieri_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Nome` TEXT NOT NULL, `Descrizione` TEXT NOT NULL, `Lunghezza` REAL NOT NULL, `Dislivello` INTEGER NOT NULL, `Discesa` INTEGER NOT NULL, `HrMed` INTEGER NOT NULL, `HrMax` INTEGER NOT NULL, `DataOra` TEXT NOT NULL, `TempMedia` REAL NOT NULL, `TempMax` REAL NOT NULL, `TempMin` REAL NOT NULL, `DataFine` TEXT NOT NULL, `TempoTot` REAL NOT NULL, `TempoInMov` REAL NOT NULL, `MediaVel` REAL NOT NULL, `uuid` TEXT NOT NULL, `lastUpdate` INTEGER NOT NULL)")
                db.execSQL("""
                    INSERT INTO `Sentieri_new` (id, Nome, Descrizione, Lunghezza, Dislivello, Discesa, HrMed, HrMax, DataOra, TempMedia, TempMax, TempMin, DataFine, TempoTot, TempoInMov, MediaVel, uuid, lastUpdate)
                    SELECT id, COALESCE(Nome, ''), COALESCE(Descrizione, ''), COALESCE(Lunghezza, 0.0), COALESCE(Dislivello, 0), COALESCE(Discesa, 0), COALESCE(HrMed, 0), COALESCE(HrMax, 0), COALESCE(DataOra, ''), COALESCE(TempMedia, 0.0), COALESCE(TempMax, 0.0), COALESCE(TempMin, 0.0), COALESCE(DataFine, ''), COALESCE(TempoTot, 0.0), COALESCE(TempoInMov, 0.0), COALESCE(MediaVel, 0.0), 
                    (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
                    strftime('%s','now') * 1000
                    FROM Sentieri
                """)
                db.execSQL("DROP TABLE Sentieri")
                db.execSQL("ALTER TABLE Sentieri_new RENAME TO Sentieri")

                // 2. Recreate Track table
                db.execSQL("CREATE TABLE IF NOT EXISTS `Track_new` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Trackid` INTEGER NOT NULL, `Lat` REAL NOT NULL, `Lon` REAL NOT NULL, `Ele` REAL NOT NULL, `Time` TEXT NOT NULL, `trackUuid` TEXT NOT NULL)")
                db.execSQL("""
                    INSERT INTO `Track_new` (Id, Trackid, Lat, Lon, Ele, Time, trackUuid)
                    SELECT Id, Trackid, COALESCE(Lat, 0.0), COALESCE(Lon, 0.0), COALESCE(Ele, 0.0), COALESCE(Time, ''),
                    COALESCE((SELECT uuid FROM Sentieri WHERE Sentieri.id = Track.Trackid), '')
                    FROM Track
                """)
                db.execSQL("DROP TABLE Track")
                db.execSQL("ALTER TABLE Track_new RENAME TO Track")

                // 3. Recreate PoiDB table
                db.execSQL("CREATE TABLE IF NOT EXISTS `PoiDB_new` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Trackid` INTEGER NOT NULL, `Lat` REAL NOT NULL, `Lon` REAL NOT NULL, `Ele` REAL NOT NULL, `NomePOI` TEXT NOT NULL, `DescrPOI` TEXT NOT NULL, `UriPath` TEXT NOT NULL, `Time` TEXT NOT NULL, `uuid` TEXT NOT NULL, `trackUuid` TEXT NOT NULL, `lastUpdate` INTEGER NOT NULL)")
                db.execSQL("""
                    INSERT INTO `PoiDB_new` (Id, Trackid, Lat, Lon, Ele, NomePOI, DescrPOI, UriPath, Time, uuid, trackUuid, lastUpdate)
                    SELECT Id, Trackid, COALESCE(Lat, 0.0), COALESCE(Lon, 0.0), COALESCE(Ele, 0.0), COALESCE(NomePOI, ''), COALESCE(DescrPOI, ''), COALESCE(UriPath, ''), COALESCE(Time, ''),
                    (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
                    COALESCE((SELECT uuid FROM Sentieri WHERE Sentieri.id = PoiDB.Trackid), ''),
                    strftime('%s','now') * 1000
                    FROM PoiDB
                """)
                db.execSQL("DROP TABLE PoiDB")
                db.execSQL("ALTER TABLE PoiDB_new RENAME TO PoiDB")

                // 4. Recreate FotoPoi table
                db.execSQL("CREATE TABLE IF NOT EXISTS `FotoPoi_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Trackid` INTEGER NOT NULL, `UriPath` TEXT NOT NULL, `NomeFoto` TEXT NOT NULL, `uuid` TEXT NOT NULL, `trackUuid` TEXT NOT NULL, `lastUpdate` INTEGER NOT NULL)")
                db.execSQL("""
                    INSERT INTO `FotoPoi_new` (id, Trackid, UriPath, NomeFoto, uuid, trackUuid, lastUpdate)
                    SELECT id, Trackid, COALESCE(UriPath, ''), COALESCE(NomeFoto, ''),
                    (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
                    COALESCE((SELECT uuid FROM Sentieri WHERE Sentieri.id = FotoPoi.Trackid), ''),
                    strftime('%s','now') * 1000
                    FROM FotoPoi
                """)
                db.execSQL("DROP TABLE FotoPoi")
                db.execSQL("ALTER TABLE FotoPoi_new RENAME TO FotoPoi")
            }
        }
    }
}