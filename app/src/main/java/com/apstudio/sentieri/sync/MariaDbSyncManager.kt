package com.apstudio.sentieri.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apstudio.sentieri.BuildConfig
import com.apstudio.sentieri.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.sql.Connection
import java.sql.DriverManager

class MariaDbSyncManager(
    private val context: Context,
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
                onProgress("Analisi dei dati in corso...")
                val (uploadedUuids, downloadedUuids) = syncSentieri(conn, onProgress)
                
                if (uploadedUuids.isNotEmpty() || downloadedUuids.isNotEmpty()) {
                    onProgress("Aggiornamento Punti Traccia...")
                    syncTracks(conn, uploadedUuids, downloadedUuids)
                    
                    onProgress("Aggiornamento POI...")
                    syncPois(conn, uploadedUuids, downloadedUuids)
                    
                    onProgress("Sincronizzazione Foto...")
                    syncFotos(conn, uploadedUuids, downloadedUuids, onProgress)
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

    private suspend fun syncSentieri(conn: Connection, onProgress: (String) -> Unit): Pair<List<String>, List<String>> {
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
                if (remoteUpdate == null) {
                    onProgress("Caricamento: ${item.nome}")
                    uploadedUuids.add(item.uuid)
                } else {
                    onProgress("Aggiornamento sul NAS: ${item.nome}")
                }
                uploadSentiero(conn, item)
            }
        }

        for ((uuid, remoteUpdate) in remoteUuids) {
            val localItem = sentieriDao.getByUuid(uuid)
            if (localItem == null || remoteUpdate > localItem.lastUpdate) {
                downloadSentiero(conn, uuid) { nomeSentiero ->
                    if (localItem == null) {
                        onProgress("Scaricamento: $nomeSentiero")
                        downloadedUuids.add(uuid)
                    } else {
                        onProgress("Aggiornamento locale: $nomeSentiero")
                    }
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

    private suspend fun downloadSentiero(conn: Connection, uuid: String, onNomeDownloaded: (String) -> Unit) {
        val sql = "SELECT * FROM Sentieri WHERE uuid = ?"
        conn.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, uuid)
            val rs = pstmt.executeQuery()
            if (rs.next()) {
                val nome = rs.getString("Nome")
                onNomeDownloaded(nome)
                val sentiero = Sentieri(
                    id = rs.getInt("id"),
                    nome = nome,
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

    private suspend fun syncFotos(conn: Connection, uploadedUuids: List<String>, downloadedUuids: List<String>, onProgress: (String) -> Unit) {
        val ftpClient = FTPClient()
        var ftpConnected = false

        fun ensureFtpConnected(): Boolean {
            if (ftpConnected) return true
            Log.d(TAG, "Tentativo di connessione FTP a ${BuildConfig.FTP_SERVER}...")
            return try {
                ftpClient.connect(BuildConfig.FTP_SERVER, BuildConfig.FTP_PORT)
                val reply = ftpClient.replyCode
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftpClient.disconnect()
                    Log.e(TAG, "Connessione FTP rifiutata: $reply")
                    return false
                }

                val loginSuccess = ftpClient.login(BuildConfig.FTP_USER, BuildConfig.FTP_PASS)
                if (!loginSuccess) {
                    Log.e(TAG, "Login FTP fallito per l'utente ${BuildConfig.FTP_USER}")
                    return false
                }
                
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                
                // Assicurati che la directory esista sul NAS (relativa alla root "cloud")
                //ftpClient.makeDirectory("SentieriFoto")
                
                if (ftpClient.changeWorkingDirectory("/SentieriFoto")) {
                    Log.d(TAG, "Connessione FTP stabilita e directory 'SentieriFoto' impostata.")
                    ftpConnected = true
                    true
                } else {
                    Log.e(TAG, "Impossibile accedere alla cartella SentieriFoto sul NAS")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore connessione FTP per foto: ${e.message}")
                false
            }
        }

        try {
            // Upload Fotos per i sentieri caricati/aggiornati
            for (trackUuid in uploadedUuids) {
                val fotos = fotoPoiDao.getFotosByTrackUuid(trackUuid)
                for (f in fotos) {
                    uploadFoto(conn, f)
                    
                    Log.d(TAG, "Processando foto: ${f.nomeFoto}, path: ${f.uriPath}")
                    
                    // Gestione upload file (sia URI che Path diretto)
                    val uri = Uri.parse(f.uriPath)
                    val inputStream: InputStream? = if (uri.scheme == "content") {
                        try {
                            context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore apertura URI content: ${e.message}")
                            null
                        }
                    } else {
                        val file = File(f.uriPath)
                        if (file.exists()) {
                            try {
                                FileInputStream(file)
                            } catch (e: Exception) {
                                Log.e(TAG, "Errore apertura file path: ${e.message}")
                                null
                            }
                        } else {
                            null
                        }
                    }

                    if (inputStream != null) {
                        if (ensureFtpConnected()) {
                            onProgress("Upload foto: ${f.nomeFoto}")
                            inputStream.use { input ->
                                val success = ftpClient.storeFile(f.nomeFoto, input)
                                if (success) {
                                    Log.d(TAG, "Upload successo: ${f.nomeFoto}")
                                } else {
                                    Log.e(TAG, "Upload fallito: ${f.nomeFoto} - Risposta: ${ftpClient.replyString}")
                                }
                            }
                        } else {
                            inputStream.close()
                        }
                    } else {
                        Log.w(TAG, "File non trovato per l'upload: ${f.uriPath}")
                    }
                }
            }

            // Download Fotos per i sentieri scaricati/aggiornati
            for (trackUuid in downloadedUuids) {
                val sql = "SELECT * FROM FotoPoi WHERE trackUuid = ?"
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, trackUuid)
                    val rs = pstmt.executeQuery()
                    while (rs.next()) {
                        val localSentiero = sentieriDao.getByUuid(trackUuid)
                        val nomeFoto = rs.getString("NomeFoto")
                        val uriPath = rs.getString("UriPath")
                        val fotoResult = FotoPoi(
                            id = rs.getInt("id"),
                            trackid = localSentiero?.id ?: 0,
                            uriPath = uriPath,
                            nomeFoto = nomeFoto,
                            uuid = rs.getString("uuid"),
                            trackUuid = trackUuid,
                            lastUpdate = rs.getLong("lastUpdate")
                        )
                        fotoPoiDao.upsert(fotoResult)
                        
                        // Download del file fisico se manca localmente
                        // Nota: il download presuppone che uriPath sia un percorso locale scrivibile
                        val uri = Uri.parse(uriPath)
                        if (uri.scheme != "content") {
                            val localFile = File(uriPath)
                            if (!localFile.exists() && ensureFtpConnected()) {
                                onProgress("Download foto: $nomeFoto")
                                localFile.parentFile?.mkdirs()
                                try {
                                    FileOutputStream(localFile).use { output ->
                                        val success = ftpClient.retrieveFile(nomeFoto, output)
                                        if (success) {
                                            Log.d(TAG, "Download successo: $nomeFoto")
                                        } else {
                                            Log.e(TAG, "Download fallito: $nomeFoto")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Errore durante il download del file: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (ftpConnected) {
                try {
                    ftpClient.logout()
                    ftpClient.disconnect()
                } catch (_: Exception) {}
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
