package com.apstudio.sentieri.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.apstudio.sentieri.BuildConfig
import com.apstudio.sentieri.MapUtils
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

    // Helper per ottenere la cartella DCIM/Sentieri standard
    private fun getStandardPhotoDir(): File {
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val sentieri = File(dcim, "Sentieri")
        if (!sentieri.exists()) sentieri.mkdirs()
        return sentieri
    }

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
                    syncPois(conn, uploadedUuids, downloadedUuids, onProgress)
                    
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

    private suspend fun syncPois(conn: Connection, uploadedUuids: List<String>, downloadedUuids: List<String>, onProgress: (String) -> Unit) {
        val ftpClient = FTPClient()
        var ftpConnected = false

        fun ensureFtpConnected(): Boolean {
            if (ftpConnected) return true
            try {
                ftpClient.connect(BuildConfig.FTP_SERVER, BuildConfig.FTP_PORT)
                if (ftpClient.login(BuildConfig.FTP_USER, BuildConfig.FTP_PASS)) {
                    ftpClient.enterLocalPassiveMode()
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                    ftpConnected = true
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore connessione FTP per audio: ${e.message}")
            }
            return false
        }

        try {
            // Upload POIs for new sentieri
            for (trackUuid in uploadedUuids) {
                val pois = poiDao.getPoisByTrackUuid(trackUuid)
                for (p in pois) {
                    uploadPoi(conn, p)
                    
                    // Upload audio file if present
                    if (p.UriPath.isNotEmpty()) {
                        val uri = Uri.parse(p.UriPath)
                        val fileName = if (uri.scheme == "content") {
                            MapUtils.getFileNameFromUri(context, uri)
                        } else {
                            File(p.UriPath).name
                        }

                        val inputStream: InputStream? = if (uri.scheme == "content") {
                            try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
                        } else {
                            val file = File(p.UriPath)
                            if (file.exists()) FileInputStream(file) else null
                        }

                        if (inputStream != null && ensureFtpConnected()) {
                            // Assicura che le cartelle esistano
                            ftpClient.makeDirectory("Sentieri")
                            ftpClient.makeDirectory("Sentieri/Audio")
                            
                            onProgress("Upload audio: $fileName")
                            inputStream.use { input ->
                                ftpClient.storeFile("Sentieri/Audio/$fileName", input)
                            }
                        } else {
                            inputStream?.close()
                        }
                    }
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
                        val remoteUuid = rs.getString("uuid")
                        val remoteUriPath = rs.getString("UriPath")
                        
                        val localPoi = poiDao.getByUuid(remoteUuid)
                        val poiResult = PoiDB(
                            Id = localPoi?.Id ?: 0,
                            Trackid = localSentiero?.id ?: 0,
                            Latit = rs.getDouble("Lat"),
                            Longit = rs.getDouble("Lon"),
                            Ele = rs.getDouble("Ele"),
                            NomePOI = rs.getString("NomePOI"),
                            DescrPOI = rs.getString("DescrPOI"),
                            UriPath = remoteUriPath,
                            Time = rs.getString("Time"),
                            uuid = remoteUuid,
                            trackUuid = trackUuid,
                            lastUpdate = rs.getLong("lastUpdate")
                        )
                        poiDao.upsert(poiResult)
                        
                        // Download audio file if missing or empty
                        if (remoteUriPath.isNotEmpty()) {
                            val fileName = remoteUriPath.substringAfterLast("/")
                            val audioDir = File(context.getExternalFilesDir(null), "VoiceNotesWaypoints")
                            val localFile = File(audioDir, fileName)
                            
                            if ((!localFile.exists() || localFile.length() == 0L) && ensureFtpConnected()) {
                                onProgress("Download audio: $fileName")
                                audioDir.mkdirs()
                                
                                val remotePath = "Sentieri/Audio/$fileName"
                                val success = downloadFileViaFtp(ftpClient, remotePath, localFile)
                                if (success) {
                                    poiResult.UriPath = localFile.absolutePath
                                    poiDao.upsert(poiResult)
                                } else {
                                    Log.e(TAG, "Download audio fallito: $fileName. Risposta: ${ftpClient.replyString}")
                                }
                            } else if (localFile.exists() && localFile.length() > 0L && poiResult.UriPath != localFile.absolutePath) {
                                // Repair path if file exists locally but path is wrong (e.g. from other device)
                                poiResult.UriPath = localFile.absolutePath
                                poiDao.upsert(poiResult)
                            }
                        }
                    }
                }
            }
        } finally {
            if (ftpConnected) {
                try { ftpClient.logout(); ftpClient.disconnect() } catch (_: Exception) {}
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

    /**
     * Download di un file via FTP ricalcando la logica di DownloadService.kt
     */
    private suspend fun downloadFileViaFtp(ftpClient: FTPClient, remotePath: String, localFile: File): Boolean {
        var success = false
        try {
            // Assicuriamoci che la modalità binaria sia attiva (fondamentale per le immagini)
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            
            val inputStream = ftpClient.retrieveFileStream(remotePath)
            if (inputStream != null) {
                FileOutputStream(localFile).use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                // Chiude esplicitamente lo stream se non già fatto da use
                try { inputStream.close() } catch (_: Exception) {}
                
                // PASSAGGIO CRUCIALE: completa il comando per liberare il socket e ricevere la risposta dal server
                success = ftpClient.completePendingCommand()
                if (!success) {
                    Log.e(TAG, "completePendingCommand fallito per $remotePath. Risposta: ${ftpClient.replyString}")
                }
            } else {
                Log.e(TAG, "retrieveFileStream restituito null per $remotePath. Risposta: ${ftpClient.replyString}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante downloadFileViaFtp di $remotePath: ${e.message}")
        } finally {
            if (!success && localFile.exists()) {
                localFile.delete()
            }
        }
        return success
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
                
                ftpConnected = true
                true
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
                            // Assicurati che le directory esistano sul NAS
                            ftpClient.makeDirectory("Sentieri")
                            ftpClient.makeDirectory("Sentieri/Foto")
                            
                            onProgress("Upload foto: ${f.nomeFoto}")
                            inputStream.use { input ->
                                val success = ftpClient.storeFile("Sentieri/Foto/${f.nomeFoto}", input)
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
                        val remoteUuid = rs.getString("uuid")
                        val remoteLastUpdate = rs.getLong("lastUpdate")
                        
                        val localFoto = fotoPoiDao.getByUuid(remoteUuid)
                        
                        val fotoResult = FotoPoi(
                            id = localFoto?.id ?: 0,
                            trackid = localSentiero?.id ?: 0,
                            uriPath = uriPath,
                            nomeFoto = nomeFoto,
                            uuid = remoteUuid,
                            trackUuid = trackUuid,
                            lastUpdate = remoteLastUpdate
                        )
                        fotoPoiDao.upsert(fotoResult)
                        
                        // Logica di download del file fisico
                        Log.d(TAG, "Verifica file per download: $nomeFoto")
                        
                        val photoDir = getStandardPhotoDir()
                        val targetFile = File(photoDir, nomeFoto)
                        
                        var needsDownload = false
                        
                        // Se il file non esiste o è vuoto (0 byte), deve essere scaricato
                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            needsDownload = true
                        } else {
                            // Se esiste ed è valido, assicuriamoci che il record nel DB locale punti qui
                            if (fotoResult.uriPath != targetFile.absolutePath) {
                                fotoResult.uriPath = targetFile.absolutePath
                                fotoPoiDao.upsert(fotoResult)
                                Log.d(TAG, "File già presente in DCIM, aggiornato path: ${targetFile.absolutePath}")
                            }
                        }

                        if (needsDownload && ensureFtpConnected()) {
                            onProgress("Download foto: $nomeFoto")
                            val remotePath = "Sentieri/Foto/$nomeFoto"
                            val success = downloadFileViaFtp(ftpClient, remotePath, targetFile)
                            if (success) {
                                Log.d(TAG, "Download successo: $nomeFoto")
                                fotoResult.uriPath = targetFile.absolutePath
                                fotoPoiDao.upsert(fotoResult)
                            } else {
                                Log.e(TAG, "Download fallito via stream per $nomeFoto")
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
