package com.apstudio.sentieri.sync

import android.util.Log
import com.apstudio.sentieri.BuildConfig
import com.apstudio.sentieri.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

class MariaDbSyncManager(
    private val sentieriDao: SentieriDao,
    private val trackDao: TrackDao,
    private val poiDao: PoiDao,
    private val fotoPoiDao: FotoPoiDao
) {
    private val TAG = "MariaDbSyncManager"
    private val connectionUrl = "jdbc:mariadb://${BuildConfig.MARIADB_HOST}/${BuildConfig.MARIADB_DB}?user=${BuildConfig.MARIADB_USER}&password=${BuildConfig.MARIADB_PASS}"

    suspend fun sync(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Connessione al database NAS...")
            Class.forName("org.mariadb.jdbc.Driver")
            val connection = DriverManager.getConnection(connectionUrl)
            
            connection.use { conn ->
                onProgress("Sincronizzazione Sentieri...")
                syncSentieri(conn)
                
                onProgress("Sincronizzazione Punti Traccia...")
                syncTracks(conn)
                
                onProgress("Sincronizzazione POI...")
                syncPois(conn)
                
                onProgress("Sincronizzazione Foto...")
                syncFotos(conn)
            }
            onProgress("Sincronizzazione completata")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la sincronizzazione", e)
            Result.failure(e)
        }
    }

    private suspend fun syncSentieri(conn: Connection) {
        val remoteUuids = mutableMapOf<String, Long>()
        val rs = conn.createStatement().executeQuery("SELECT uuid, lastUpdate FROM Sentieri")
        while (rs.next()) {
            remoteUuids[rs.getString("uuid")] = rs.getLong("lastUpdate")
        }

        val localItems = sentieriDao.getTuttiSentieriList()
        for (item in localItems) {
            val remoteUpdate = remoteUuids[item.uuid]
            if (remoteUpdate == null || item.lastUpdate > remoteUpdate) {
                uploadSentiero(conn, item)
            }
        }

        for ((uuid, remoteUpdate) in remoteUuids) {
            val localItem = sentieriDao.getByUuid(uuid)
            if (localItem == null || remoteUpdate > localItem.lastUpdate) {
                downloadSentiero(conn, uuid)
            }
        }
    }

    private fun uploadSentiero(conn: Connection, item: Sentieri) {
        val sql = """
            INSERT INTO Sentieri (Nome, Descrizione, Lunghezza, Dislivello, Discesa, HrMed, HrMax, DataOra, TempMedia, TempMax, TempMin, DataFine, TempoTot, TempoInMov, MediaVel, uuid, lastUpdate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            Nome=VALUES(Nome), Descrizione=VALUES(Descrizione), Lunghezza=VALUES(Lunghezza), Dislivello=VALUES(Dislivello), 
            Discesa=VALUES(Discesa), HrMed=VALUES(HrMed), HrMax=VALUES(HrMax), DataOra=VALUES(DataOra), 
            TempMedia=VALUES(TempMedia), TempMax=VALUES(TempMax), TempMin=VALUES(TempMin), DataFine=VALUES(DataFine), 
            TempoTot=VALUES(TempoTot), TempoInMov=VALUES(TempoInMov), MediaVel=VALUES(MediaVel), lastUpdate=VALUES(lastUpdate)
        """.trimIndent()
        
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, item.nome)
            pstmt.setString(2, item.descrizione)
            pstmt.setDouble(3, item.lunghezza)
            pstmt.setInt(4, item.dislivello)
            pstmt.setInt(5, item.discesa)
            pstmt.setInt(6, item.HrMed)
            pstmt.setInt(7, item.HrMax)
            pstmt.setString(8, item.DataOra)
            pstmt.setDouble(9, item.TempMedia)
            pstmt.setDouble(10, item.TempMax)
            pstmt.setDouble(11, item.TempMin)
            pstmt.setString(12, item.DataFine)
            pstmt.setDouble(13, item.TempoTot)
            pstmt.setDouble(14, item.TempoInMov)
            pstmt.setDouble(15, item.MediaVel)
            pstmt.setString(16, item.uuid)
            pstmt.setLong(17, item.lastUpdate)
            pstmt.executeUpdate()
        }
    }

    private suspend fun downloadSentiero(conn: Connection, uuid: String) {
        val sql = "SELECT * FROM Sentieri WHERE uuid = ?"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, uuid)
            val rs = pstmt.executeQuery()
            if (rs.next()) {
                val sentiero = Sentieri(
                    nome = rs.getString("Nome"),
                    descrizione = rs.getString("Descrizione"),
                    lunghezza = rs.getDouble("Lunghezza"),
                    dislivello = rs.getInt("Dislivello"),
                    discesa = rs.getInt("Discesa"),
                    HrMed = rs.getInt("HrMed"),
                    HrMax = rs.getInt("HrMax"),
                    DataOra = rs.getString("DataOra"),
                    TempMedia = rs.getDouble("TempMedia"),
                    TempMax = rs.getDouble("TempMax"),
                    TempMin = rs.getDouble("TempMin"),
                    DataFine = rs.getString("DataFine"),
                    TempoTot = rs.getDouble("TempoTot"),
                    TempoInMov = rs.getDouble("TempoInMov"),
                    MediaVel = rs.getDouble("MediaVel"),
                    uuid = rs.getString("uuid"),
                    lastUpdate = rs.getLong("lastUpdate")
                )
                sentieriDao.upsert(sentiero)
            }
        }
    }

    private suspend fun syncTracks(conn: Connection) {
        val localTrackUuids = trackDao.getAllTrackUuids()
        val remoteTrackUuids = mutableSetOf<String>()
        val rs = conn.createStatement().executeQuery("SELECT DISTINCT trackUuid FROM Track")
        while (rs.next()) {
            remoteTrackUuids.add(rs.getString("trackUuid"))
        }

        for (trackUuid in localTrackUuids) {
            if (!remoteTrackUuids.contains(trackUuid)) {
                uploadTrack(conn, trackUuid)
            }
        }

        for (trackUuid in remoteTrackUuids) {
            if (!localTrackUuids.contains(trackUuid)) {
                downloadTrack(conn, trackUuid)
            }
        }
    }

    private suspend fun uploadTrack(conn: Connection, trackUuid: String) {
        val points = trackDao.getPointsByTrackUuid(trackUuid)
        val sql = "INSERT INTO Track (trackUuid, Lat, Lon, Ele, Time) VALUES (?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { pstmt ->
            for (p in points) {
                pstmt.setString(1, trackUuid)
                pstmt.setFloat(2, p.Latit)
                pstmt.setFloat(3, p.Longit)
                pstmt.setFloat(4, p.Ele)
                pstmt.setString(5, p.Ora)
                pstmt.addBatch()
            }
            pstmt.executeBatch()
        }
    }

    private suspend fun downloadTrack(conn: Connection, trackUuid: String) {
        val localSentiero = sentieriDao.getByUuid(trackUuid) ?: return
        val localTrackId = localSentiero.id
        
        val sql = "SELECT * FROM Track WHERE trackUuid = ?"
        val points = mutableListOf<Track>()
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, trackUuid)
            val rs = pstmt.executeQuery()
            while (rs.next()) {
                points.add(Track(
                    Trackid = localTrackId,
                    Latit = rs.getFloat("Lat"),
                    Longit = rs.getFloat("Lon"),
                    Ele = rs.getFloat("Ele"),
                    Ora = rs.getString("Time"),
                    trackUuid = rs.getString("trackUuid")
                ))
            }
        }
        if (points.isNotEmpty()) {
            trackDao.upsertPoints(points)
        }
    }

    private suspend fun syncPois(conn: Connection) {
        val remoteUuids = mutableMapOf<String, Long>()
        val rs = conn.createStatement().executeQuery("SELECT uuid, lastUpdate FROM PoiDB")
        while (rs.next()) {
            remoteUuids[rs.getString("uuid")] = rs.getLong("lastUpdate")
        }

        val localItems = poiDao.listPoiDB()
        for (item in localItems) {
            val remoteUpdate = remoteUuids[item.uuid]
            if (remoteUpdate == null || item.lastUpdate > remoteUpdate) {
                uploadPoi(conn, item)
            }
        }

        for ((uuid, remoteUpdate) in remoteUuids) {
            val localItem = poiDao.getByUuid(uuid)
            if (localItem == null || remoteUpdate > localItem.lastUpdate) {
                downloadPoi(conn, uuid)
            }
        }
    }

    private fun uploadPoi(conn: Connection, item: PoiDB) {
        val sql = """
            INSERT INTO PoiDB (trackUuid, Lat, Lon, Ele, NomePOI, DescrPOI, UriPath, Time, uuid, lastUpdate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            Lat=VALUES(Lat), Lon=VALUES(Lon), Ele=VALUES(Ele), NomePOI=VALUES(NomePOI), 
            DescrPOI=VALUES(DescrPOI), UriPath=VALUES(UriPath), Time=VALUES(Time), lastUpdate=VALUES(lastUpdate)
        """.trimIndent()
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, item.trackUuid)
            pstmt.setDouble(2, item.Latit)
            pstmt.setDouble(3, item.Longit)
            pstmt.setDouble(4, item.Ele)
            pstmt.setString(5, item.NomePOI)
            pstmt.setString(6, item.DescrPOI)
            pstmt.setString(7, item.UriPath)
            pstmt.setString(8, item.Time)
            pstmt.setString(9, item.uuid)
            pstmt.setLong(10, item.lastUpdate)
            pstmt.executeUpdate()
        }
    }

    private suspend fun downloadPoi(conn: Connection, uuid: String) {
        val sql = "SELECT * FROM PoiDB WHERE uuid = ?"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, uuid)
            val rs = pstmt.executeQuery()
            if (rs.next()) {
                val trackUuid = rs.getString("trackUuid")
                val localSentiero = sentieriDao.getByUuid(trackUuid)
                val poi = PoiDB(
                    Trackid = localSentiero?.id ?: 0,
                    Latit = rs.getDouble("Lat"),
                    Longit = rs.getDouble("Lon"),
                    Ele = rs.getDouble("Ele"),
                    NomePOI = rs.getString("NomePOI"),
                    DescrPOI = rs.getString("DescrPOI"),
                    UriPath = rs.getString("UriPath"),
                    Time = rs.getString("Time"),
                    uuid = rs.getString("uuid"),
                    trackUuid = trackUuid,
                    lastUpdate = rs.getLong("lastUpdate")
                )
                poiDao.upsert(poi)
            }
        }
    }

    private suspend fun syncFotos(conn: Connection) {
        val remoteUuids = mutableMapOf<String, Long>()
        val rs = conn.createStatement().executeQuery("SELECT uuid, lastUpdate FROM FotoPoi")
        while (rs.next()) {
            remoteUuids[rs.getString("uuid")] = rs.getLong("lastUpdate")
        }

        val localItems = fotoPoiDao.listFotoPoiDB()
        for (item in localItems) {
            val remoteUpdate = remoteUuids[item.uuid]
            if (remoteUpdate == null || item.lastUpdate > remoteUpdate) {
                uploadFoto(conn, item)
            }
        }

        for ((uuid, remoteUpdate) in remoteUuids) {
            val localItem = fotoPoiDao.getByUuid(uuid)
            if (localItem == null || remoteUpdate > localItem.lastUpdate) {
                downloadFoto(conn, uuid)
            }
        }
    }

    private fun uploadFoto(conn: Connection, item: FotoPoi) {
        val sql = "INSERT INTO FotoPoi (trackUuid, UriPath, NomeFoto, uuid, lastUpdate) VALUES (?, ?, ?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE UriPath=VALUES(UriPath), NomeFoto=VALUES(NomeFoto), lastUpdate=VALUES(lastUpdate)"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, item.trackUuid)
            pstmt.setString(2, item.uriPath)
            pstmt.setString(3, item.nomeFoto)
            pstmt.setString(4, item.uuid)
            pstmt.setLong(5, item.lastUpdate)
            pstmt.executeUpdate()
        }
    }

    private suspend fun downloadFoto(conn: Connection, uuid: String) {
        val sql = "SELECT * FROM FotoPoi WHERE uuid = ?"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, uuid)
            val rs = pstmt.executeQuery()
            if (rs.next()) {
                val trackUuid = rs.getString("trackUuid")
                val localSentiero = sentieriDao.getByUuid(trackUuid)
                val foto = FotoPoi(
                    trackid = localSentiero?.id ?: 0,
                    uriPath = rs.getString("UriPath"),
                    nomeFoto = rs.getString("NomeFoto"),
                    uuid = rs.getString("uuid"),
                    trackUuid = trackUuid,
                    lastUpdate = rs.getLong("lastUpdate")
                )
                fotoPoiDao.upsert(foto)
            }
        }
    }
}
