package com.apstudio.sentieri.db

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow

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

    // --- OPERAZIONI COMBINATE (TRANSAZIONI) ---

    suspend fun cancellaSentieroCompleto(idSentiero: Int, context: android.content.Context) {
        // 1. RECUPERO DATI PRIMA DELLA CANCELLAZIONE
        // Recuperiamo le liste di foto e POI associate al sentiero prima di eliminarle dal DB
        val fotos = fotoPoiDao.getFotoPoibyID(idSentiero)
        val pois = poiDao.getPoibyID(idSentiero)

        // 2. ELIMINAZIONE FOTO DAL FILESYSTEM
        fotos.forEach { foto ->
            try {
                val file = java.io.File(foto.uriPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.d("SentieriRepo", "Errore eliminazione foto: ${foto.uriPath}")
            }
        }

        // 3. ELIMINAZIONE WAYPOINT VOCALI DAL FILESYSTEM
        val voceDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).parentFile?.let {
            java.io.File(it, "Android/media/com.apstudio.sentieri/VoceWaypoint")
        } ?: java.io.File(context.externalMediaDirs.firstOrNull() ?: context.getExternalFilesDir(null), "VoceWaypoint")

        pois.forEach { poi ->
            if (poi.UriPath.isNotBlank()) {
                try {
                    val fileName = poi.UriPath.substringAfterLast('/')
                    val fileVoce = java.io.File(voceDir, fileName)
                    if (fileVoce.exists()) {
                        fileVoce.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SentieriRepo", "Errore eliminazione voce: ${poi.UriPath}", e)
                }
            }
        }

        // 4. CANCELLAZIONE DAL DATABASE
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
