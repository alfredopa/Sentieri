package com.apstudio.sentieri.db

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow

/**
 * Repository che gestisce tutte le interazioni con il database.
 * È l'unica classe che dovrebbe parlare direttamente con i DAO.
 * Il ViewModel parlerà solo con questo Repository.
 */
class SentieriRepo(
    private val sentieriDao: SentieriDao,
    private val poiDao: PoiDao,
    private val fotoPoiDao: FotoPoiDao,
    private val trackDao: TrackDao
) {

    // --- METODI PER I SENTIERI ---

    fun getTuttiSentieri(): Flow<List<Sentieri>> {
        return sentieriDao.getItems()
    }

    fun cercaId(id: Int): LiveData<Sentieri> {
        return sentieriDao.getItem(id).asLiveData()
    }

    fun cercaNome(searchQuery: String): Flow<List<Sentieri>> {
        return sentieriDao.cercaNome(searchQuery)
    }

    suspend fun insertSentiero(sentiero: Sentieri): Long {
        return sentieriDao.insertDB(sentiero)
    }

    suspend fun ultimoIdSentiero(): Int {
        return sentieriDao.ultimoId()
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

    suspend fun cancellaSentieroCompleto(idSentiero: Int) {
        // KSP e Room gestiscono le transazioni automaticamente se
        // il metodo del DAO è annotato con @Transaction.
        // Se non lo è, eseguire le operazioni in sequenza è comunque sicuro.
        trackDao.deleteTrack(idSentiero)
        // Aggiungi qui la cancellazione dei POI e delle foto se necessario
        sentieriDao.deleteSentiero(idSentiero)
    }
}
