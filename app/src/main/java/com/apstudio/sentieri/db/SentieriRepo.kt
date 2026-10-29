package com.apstudio.sentieri.db

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Repository che gestisce tutte le interazioni con il database.
 * È l'unica classe che dovrebbe parlare direttamente con i DAO.
 * Il ViewModel parlerà solo con questo Repository.
 */
    class SentieriRepo(
    val sentieriDao: SentieriDao,
    val poiDao: PoiDao,
    val fotoPoiDao: FotoPoiDao,
    val trackDao: TrackDao
) {

    // --- METODI PER I SENTIERI ---

    fun getTuttiSentieri(): Flow<List<Sentieri>> {
        return sentieriDao.getItems()
    }

    fun cercaId(id: Int): LiveData<Sentieri?> {
        return sentieriDao.getItem(id).asLiveData()
    }

    fun cercaNome(searchQuery: String): Flow<List<Sentieri>> {
        return sentieriDao.cercaNome(searchQuery)
    }

    fun getSentieriPerData(dateQuery: String): Flow<List<Sentieri>> {
        return sentieriDao.getSentieriPerData(dateQuery)
    }

    fun getGiorniConRegistrazioni(): Flow<List<String>> {
        return sentieriDao.getGiorniConRegistrazioni()
    }

    suspend fun insertSentiero(sentiero: Sentieri): Long {
        return sentieriDao.insertDB(sentiero)
    }

    suspend fun rinominaSentiero(idSentiero: Int, nuovoNome: String) {
        val sentiero = sentieriDao.getItemSync(idSentiero)
        sentiero?.let {
            it.nome = nuovoNome
            sentieriDao.updateDB(it)
        }
    }

    // --- METODI PER I PUNTI DELLA TRACCIA (TRACK) ---

    fun getPuntiTraccia(idTraccia: Int): List<Track> {
        // Usa il trackDao passato nel costruttore
        return trackDao.getTraccia(idTraccia)
    }

    // --- METODI PER I PUNTI DI INTERESSE (POI) ---

    fun getPuntiPoi(idTraccia: Int): List<PoiDB> {
        return poiDao.getPoibyID(idTraccia)
    }

    fun getFotoPoi(idPoi: Int): List<FotoPoi> {
        return fotoPoiDao.getFotoPoibyID(idPoi)
    }

    // --- OPERAZIONI COMBINATE (TRANSAZIONI E PULIZIA) ---

    suspend fun cancellaSentieroCompleto(idSentiero: Int, context: Context) {
        // 1. RECUPERO DATI PRIMA DELLA CANCELLAZIONE DAL DATABASE
        val fotos = fotoPoiDao.getFotoPoibyID(idSentiero)
        val pois = poiDao.getPoibyID(idSentiero)

        @Suppress("DEPRECATION")
        val appMediaDir = try {
            context.externalMediaDirs.firstOrNull() ?: context.getExternalFilesDir(null) ?: context.filesDir
        } catch (_: Exception) {
            context.filesDir
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).parentFile

        // 2. ELIMINAZIONE FOTO DAL FILESYSTEM
        val cartelleFoto = listOfNotNull(
            appMediaDir?.let { File(it, "Foto") },
            downloadsDir?.let { File(it, "Android/media/com.apstudio.sentieri/Foto") },
            context.getExternalFilesDir("Foto"),
            context.getExternalFilesDir(null)
        )

        fotos.forEach { foto ->
            val rawPath = foto.uriPath.trim()
            val fileName = if (foto.nomeFoto.isNotBlank()) foto.nomeFoto.trim() else rawPath.substringAfterLast('/')

            // A. Se è un Content URI, tenta eliminazione tramite ContentResolver
            if (rawPath.startsWith("content://")) {
                try {
                    context.contentResolver.delete(Uri.parse(rawPath), null, null)
                } catch (e: Exception) {
                    Log.w("SentieriRepo", "Impossibile eliminare content URI foto: $rawPath - ${e.message}")
                }
            }

            // B. Eliminazione da percorsi di file fisici
            val fileDaCancellare = mutableListOf<File>()
            if (rawPath.isNotBlank() && !rawPath.startsWith("content://")) {
                fileDaCancellare.add(File(rawPath.removePrefix("file://")))
            }
            if (fileName.isNotBlank()) {
                cartelleFoto.forEach { dir ->
                    fileDaCancellare.add(File(dir, fileName))
                }
            }

            fileDaCancellare.forEach { file ->
                try {
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d("SentieriRepo", "Cancellazione foto '${file.absolutePath}': $deleted")
                    }
                } catch (e: Exception) {
                    Log.w("SentieriRepo", "Errore cancellazione file foto '${file.absolutePath}': ${e.message}")
                }
            }

            // C. Eliminazione da MediaStore (se registrata)
            if (fileName.isNotBlank()) {
                try {
                    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf(fileName)
                    context.contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        selection,
                        selectionArgs
                    )
                } catch (_: Exception) {}
            }
        }

        // 3. ELIMINAZIONE WAYPOINT VOCALI DAL FILESYSTEM
        val cartelleVoce = listOfNotNull(
            appMediaDir?.let { File(it, "VoceWaypoint") },
            appMediaDir?.let { File(it, "VoceWaypoints") },
            downloadsDir?.let { File(it, "Android/media/com.apstudio.sentieri/VoceWaypoint") },
            context.getExternalFilesDir("VoiceNotesWaypoint"),
            context.getExternalFilesDir("VoiceNotesWaypoints")
        )

        pois.forEach { poi ->
            val rawPath = poi.UriPath.trim()
            if (rawPath.isNotBlank()) {
                val fileName = rawPath.substringAfterLast('/')

                // A. Se è un Content URI
                if (rawPath.startsWith("content://")) {
                    try {
                        context.contentResolver.delete(Uri.parse(rawPath), null, null)
                    } catch (e: Exception) {
                        Log.w("SentieriRepo", "Impossibile eliminare content URI voce: $rawPath - ${e.message}")
                    }
                }

                // B. Eliminazione file fisici
                val fileDaCancellare = mutableListOf<File>()
                if (!rawPath.startsWith("content://")) {
                    fileDaCancellare.add(File(rawPath.removePrefix("file://")))
                }
                if (fileName.isNotBlank()) {
                    cartelleVoce.forEach { dir ->
                        fileDaCancellare.add(File(dir, fileName))
                    }
                }

                fileDaCancellare.forEach { file ->
                    try {
                        if (file.exists()) {
                            val deleted = file.delete()
                            Log.d("SentieriRepo", "Cancellazione file audio '${file.absolutePath}': $deleted")
                        }
                    } catch (e: Exception) {
                        Log.w("SentieriRepo", "Errore cancellazione file audio '${file.absolutePath}': ${e.message}")
                    }
                }
            }
        }

        // 4. CANCELLAZIONE DAL DATABASE ROOM
        fotoPoiDao.deleteByTrackId(idSentiero)
        poiDao.deleteByTrackId(idSentiero)
        trackDao.deleteTrack(idSentiero)
        sentieriDao.deleteSentiero(idSentiero)
    }


    suspend fun cercaInArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<Sentieri> {
        val ids = trackDao.getTrackIdsInArea(minLat.toFloat(), maxLat.toFloat(), minLon.toFloat(), maxLon.toFloat())
        val sentieri = mutableListOf<Sentieri>()
        ids.forEach { id ->
            sentieriDao.getItemSync(id)?.let { sentieri.add(it) }
        }
        return sentieri
    }
}
