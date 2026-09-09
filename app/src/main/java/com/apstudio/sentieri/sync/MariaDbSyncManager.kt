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
                val (uploadedUuids, downloadedUuids) = syncSentieri(conn)
                
                if (uploadedUuids.isNotEmpty() || downloadedUuids.isNotEmpty()) {
                    onProgress("Sincronizzazione Punti Traccia...")
                    syncTracks(conn, uploadedUuids, downloadedUuids)
                    
                    onProgress("Sincronizzazione POI...")
                    syncPois(conn, uploadedUuids, downloadedUuids)
                    
                    onProgress("Sincronizzazione Foto...")
                    syncFotos(conn, uploadedUuids, downloadedUuids)
                } else {
                    onProgress("Nessun aggiornamento necessario.")
                }
            }
            onProgress("Sincronizzazione completata")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la sincronizzazione", e)
            Result.failure(e)
        }
    }

    private suspend fun syncSentieri(conn: Connection): Pair<List<String>, List<String>> {
        val uploadedUuids = mutableListOf<String>()
        val downloadedUuids = mutableListOf<String>()
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
                if (remoteUpdate == null) {
                    uploadedUuids.add(item.uuid)
                }
            }
        }

        for ((uuid, remoteUpdate) in remoteUuids) {
            val localItem = sentieriDao.getByUuid(uuid)
            if (localItem == null || remoteUpdate > localItem.lastUpdate) {
                downloadSentiero(conn, uuid)
                if (localItem == null) {
                    downloadedUuids.add(uuid)
                }
            }
        }
        return Pair(uploadedUuids, downloadedUuids)
    }

    private fun uploadSentiero(conn: Connection, item: Sentieri) {
        val sql = """
            INSERT INTO Sentieri (id, Nome, Descrizione, Lunghezza, Dislivello, Discesa, HrMed, HrMax, DataOra, TempMedia, TempMax, TempMin, DataFine, TempoTot, TempoInMov, MediaVel, uuid, lastUpdate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            Nome=VALUES(Nome), Descrizione=VALUES(Descrizione), Lunghezza=VALUES(Lunghezza), Dislivello=VALUES(Dislivello), 
            Discesa=VALUES(Discesa), HrMed=VALUES(HrMed), HrMax=VALUES(HrMax), DataOra=VALUES(DataOra), 
            TempMedia=VALUES(TempMedia), TempMax=VALUES(TempMax), TempMin=VALUES(TempMin), DataFine=VALUES(DataFine), 
            TempoTot=VALUES(TempoTot), TempoInMov=VALUES(TempoInMov), MediaVel=VALUES(MediaVel), lastUpdate=VALUES(lastUpdate)
        """.trimIndent()
        
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, item.id)
            pstmt.setString(2, item.nome)
            pstmt.setString(3, item.descrizione)
            pstmt.setDouble(4, item.lunghezza)
            pstmt.setInt(5, item.dislivello)
            pstmt.setInt(6, item.discesa)
            pstmt.setInt(7, item.HrMed)
            pstmt.setInt(8, item.HrMax)
            pstmt.setString(9, item.DataOra)
            pstmt.setDouble(10, item.TempMedia)
            pstmt.setDouble(11, item.TempMax)
            pstmt.setDouble(12, item.TempMin)
            pstmt.setString(13, item.DataFine)
            pstmt.setDouble(14, item.TempoTot)
            pstmt.setDouble(15, item.TempoInMov)
            pstmt.setDouble(16, item.MediaVel)
            pstmt.setString(17, item.uuid)
            pstmt.setLong(18, item.lastUpdate)
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
                    id = rs.getInt("id"),
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

    private suspend fun syncTracks(conn: Connection, uploadedUuids: List<String>, downloadedUuids: List<String>) {
        for (uuid in uploadedUuids) {
            uploadTrack(conn, uuid)
        }
        for (uuid in downloadedUuids) {
            downloadTrack(conn, uuid)
        }
    }

    private suspend fun uploadTrack(conn: Connection, trackUuid: String) {
        val points = trackDao.getPointsByTrackUuid(trackUuid)
        val sql = "INSERT INTO Track (id, Trackid, trackUuid, Lat, Lon, Ele, Time) VALUES (?, ?, ?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { pstmt ->
            for (p in points) {
                pstmt.setInt(1, p.Id)
                pstmt.setInt(2, p.Trackid)
                pstmt.setString(3, trackUuid)
                pstmt.setFloat(4, p.Latit)
                pstmt.setFloat(5, p.Longit)
                pstmt.setFloat(6, p.Ele)
                pstmt.setString(7, p.Ora)
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
                    Id = rs.getInt("id"),
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

    private suspend fun syncPois(conn: Connection, uploadedUuids: List<String>, downloadedUuids: List<String>) {
        // Upload POIs for new sentieri
        for (trackUuid in uploadedUuids) {
            val pois = poiDao.getPoisByTrackUuid(trackUuid)
            for (p in pois) {
                uploadPoi(conn, p)
            }
        }

        // Download POIs for new sentieri
        for (trackUuid in downloadedUuids) {
            val sql = "SELECT * FROM PoiDB WHERE trackUuid = ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, trackUuid)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val localSentiero = sentieriDao.getByUuid(trackUuid)
                    val poiResult = PoiDB(
                        Id = rs.getInt("id"),
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
                    poiDao.upsert(poiResult)
                }
            }
        }
    }

    private fun uploadPoi(conn: Connection, item: PoiDB) {
        val sql = """
            INSERT INTO PoiDB (id, Trackid, trackUuid, Lat, Lon, Ele, NomePOI, DescrPOI, UriPath, Time, uuid, lastUpdate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            Lat=VALUES(Lat), Lon=VALUES(Lon), Ele=VALUES(Ele), NomePOI=VALUES(NomePOI), 
            DescrPOI=VALUES(DescrPOI), UriPath=VALUES(UriPath), Time=VALUES(Time), lastUpdate=VALUES(lastUpdate)
        """.trimIndent()
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, item.Id)
            pstmt.setInt(2, item.Trackid)
            pstmt.setString(3, item.trackUuid)
            pstmt.setDouble(4, item.Latit)
            pstmt.setDouble(5, item.Longit)
            pstmt.setDouble(6, item.Ele)
            pstmt.setString(7, item.NomePOI)
            pstmt.setString(8, item.DescrPOI)
            pstmt.setString(9, item.UriPath)
            pstmt.setString(10, item.Time)
            pstmt.setString(11, item.uuid)
            pstmt.setLong(12, item.lastUpdate)
            pstmt.executeUpdate()
        }
    }

    private suspend fun syncFotos(conn: Connection, uploadedUuids: List<String>, downloadedUuids: List<String>) {
        // Upload Fotos for new sentieri
        for (trackUuid in uploadedUuids) {
            val fotos = fotoPoiDao.getFotosByTrackUuid(trackUuid)
            for (f in fotos) {
                uploadFoto(conn, f)
            }
        }

        // Download Fotos for new sentieri
        for (trackUuid in downloadedUuids) {
            val sql = "SELECT * FROM FotoPoi WHERE trackUuid = ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, trackUuid)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val localSentiero = sentieriDao.getByUuid(trackUuid)
                    val fotoResult = FotoPoi(
                        id = rs.getInt("id"),
                        trackid = localSentiero?.id ?: 0,
                        uriPath = rs.getString("UriPath"),
                        nomeFoto = rs.getString("NomeFoto"),
                        uuid = rs.getString("uuid"),
                        trackUuid = trackUuid,
                        lastUpdate = rs.getLong("lastUpdate")
                    )
                    fotoPoiDao.upsert(fotoResult)
                }
            }
        }
    }

    private fun uploadFoto(conn: Connection, item: FotoPoi) {
        val sql = "INSERT INTO FotoPoi (id, Trackid, trackUuid, UriPath, NomeFoto, uuid, lastUpdate) VALUES (?, ?, ?, ?, ?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE UriPath=VALUES(UriPath), NomeFoto=VALUES(NomeFoto), lastUpdate=VALUES(lastUpdate)"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, item.id)
            pstmt.setInt(2, item.trackid)
            pstmt.setString(3, item.trackUuid)
            pstmt.setString(4, item.uriPath)
            pstmt.setString(5, item.nomeFoto)
            pstmt.setString(6, item.uuid)
            pstmt.setLong(7, item.lastUpdate)
            pstmt.executeUpdate()
        }
    }
}
