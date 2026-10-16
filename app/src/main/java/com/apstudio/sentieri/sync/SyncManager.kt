package com.apstudio.sentieri.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.MapUtils
import com.apstudio.sentieri.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import com.apstudio.sentieri.sync.DownloadService.ServerConfig

private const val TAG = "SyncManager"

// ───────────────────────── Configurazione ─────────────────────────

data class SyncConfig(
    val baseUrl: String,     // es. https://apstudio01.duckdns.org
    val token: String        // Token bearer impostato nelle preferenze
) {
    val base: String get() = baseUrl.trimEnd('/').removeSuffix("/files") // arriva con la stringa files, va tolta
}

class SyncHttpException(val code: Int, message: String) : IOException(message)

// ───────────────────────── DTO ─────────────────────────

private fun JSONObject.d(k: String) = optDouble(k, 0.0)
private fun JSONObject.i(k: String) = optInt(k, 0)
private fun JSONObject.s(k: String) = optString(k, "")
private fun JSONObject.optNullableInt(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)
private fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

data class SentieroDto(
    val uuid: String, val nome: String, val descrizione: String,
    val lunghezza: Double, val dislivello: Int, val discesa: Int,
    val hrMed: Int, val hrMax: Int, val dataOra: String,
    val tempMedia: Double, val tempMax: Double, val tempMin: Double,
    val dataFine: String, val tempoTot: Double, val tempoInMov: Double,
    val mediaVel: Double, val lastUpdate: Long,
    val nPunti: Int = 0                      // solo in lettura: punti presenti sul server
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid).put("Nome", nome).put("Descrizione", descrizione)
        .put("Lunghezza", lunghezza).put("Dislivello", dislivello).put("Discesa", discesa)
        .put("HrMed", hrMed).put("HrMax", hrMax).put("DataOra", dataOra)
        .put("TempMedia", tempMedia).put("TempMax", tempMax).put("TempMin", tempMin)
        .put("DataFine", dataFine).put("TempoTot", tempoTot).put("TempoInMov", tempoInMov)
        .put("MediaVel", mediaVel).put("lastUpdate", lastUpdate)

    companion object {
        fun fromJson(o: JSONObject) = SentieroDto(
            uuid = o.getString("uuid"), nome = o.s("Nome"), descrizione = o.s("Descrizione"),
            lunghezza = o.d("Lunghezza"), dislivello = o.i("Dislivello"), discesa = o.i("Discesa"),
            hrMed = o.i("HrMed"), hrMax = o.i("HrMax"), dataOra = o.s("DataOra"),
            tempMedia = o.d("TempMedia"), tempMax = o.d("TempMax"), tempMin = o.d("TempMin"),
            dataFine = o.s("DataFine"), tempoTot = o.d("TempoTot"), tempoInMov = o.d("TempoInMov"),
            mediaVel = o.d("MediaVel"), lastUpdate = o.optLong("lastUpdate", 0L), nPunti = o.i("nPunti")
        )

        fun fromEntity(e: Sentieri) = SentieroDto(
            uuid = e.uuid, nome = e.nome, descrizione = e.descrizione,
            lunghezza = e.lunghezza, dislivello = e.dislivello, discesa = e.discesa,
            hrMed = e.HrMed, hrMax = e.HrMax, dataOra = e.DataOra,
            tempMedia = e.TempMedia, tempMax = e.TempMax, tempMin = e.TempMin,
            dataFine = e.DataFine, tempoTot = e.TempoTot, tempoInMov = e.TempoInMov,
            mediaVel = e.MediaVel, lastUpdate = e.lastUpdate
        )
    }
}

data class PuntoDto(
    val lat: Double,
    val lon: Double,
    val ele: Double,
    val time: String,
    val batteryPercent: Int? = null,
    val cadence: Int? = null,
    val assistLevel: Int? = null,
    val power: Int? = null,
    val heartRate: Int? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("Lat", lat)
        .put("Lon", lon)
        .put("Ele", ele)
        .put("Time", time)
        .apply {
            batteryPercent?.let { put("batteryPercent", it) }
            cadence?.let { put("cadence", it) }
            assistLevel?.let { put("assistLevel", it) }
            power?.let { put("power", it) }
            heartRate?.let { put("heartRate", it) }
        }

    companion object {
        fun fromJson(o: JSONObject) = PuntoDto(
            lat = o.d("Lat"),
            lon = o.d("Lon"),
            ele = o.d("Ele"),
            time = o.s("Time"),
            batteryPercent = o.optNullableInt("batteryPercent"),
            cadence = o.optNullableInt("cadence"),
            assistLevel = o.optNullableInt("assistLevel"),
            power = o.optNullableInt("power"),
            heartRate = o.optNullableInt("heartRate")
        )

        fun fromEntity(e: Track) = PuntoDto(
            lat = e.Latit.toDouble(),
            lon = e.Longit.toDouble(),
            ele = e.Ele.toDouble(),
            time = e.Ora,
            batteryPercent = e.batteryPercent,
            cadence = e.cadence,
            assistLevel = e.assistLevel,
            power = e.power,
            heartRate = e.heartRate
        )
    }
}

data class PoiDto(
    val uuid: String, val trackUuid: String,
    val lat: Double, val lon: Double, val ele: Double,
    val nome: String, val descr: String, val time: String,
    val uriPath: String = "", val lastUpdate: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid).put("trackUuid", trackUuid)
        .put("Lat", lat).put("Lon", lon).put("Ele", ele)
        .put("NomePOI", nome).put("DescrPOI", descr)
        .put("Time", time).put("UriPath", uriPath)
        .put("lastUpdate", lastUpdate)

    companion object {
        fun fromJson(o: JSONObject) = PoiDto(
            uuid = o.getString("uuid"), trackUuid = o.s("trackUuid"),
            lat = o.d("Lat"), lon = o.d("Lon"), ele = o.d("Ele"),
            nome = o.s("NomePOI"), descr = o.s("DescrPOI"),
            time = o.s("Time"), uriPath = o.s("UriPath"),
            lastUpdate = o.optLong("lastUpdate", 0L)
        )

        fun fromEntity(e: PoiDB) = PoiDto(
            uuid = e.uuid, trackUuid = e.trackUuid,
            lat = e.Latit, lon = e.Longit, ele = e.Ele,
            nome = e.NomePOI, descr = e.DescrPOI,
            time = e.Time, uriPath = e.UriPath,
            lastUpdate = e.lastUpdate
        )
    }
}

data class FotoDto(
    val uuid: String, val trackUuid: String, val nomeFoto: String,
    val uriPath: String = "", val lastUpdate: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid).put("trackUuid", trackUuid)
        .put("NomeFoto", nomeFoto).put("UriPath", uriPath)
        .put("lastUpdate", lastUpdate)

    companion object {
        fun fromJson(o: JSONObject) = FotoDto(
            uuid = o.getString("uuid"), trackUuid = o.s("trackUuid"),
            nomeFoto = o.s("NomeFoto"), uriPath = o.s("UriPath"),
            lastUpdate = o.optLong("lastUpdate", 0L)
        )

        fun fromEntity(e: FotoPoi) = FotoDto(
            uuid = e.uuid, trackUuid = e.trackUuid,
            nomeFoto = e.nomeFoto, uriPath = e.uriPath,
            lastUpdate = e.lastUpdate
        )
    }
}

// ───────────────────────── Client HTTP per Backend NAS ─────────────────────────

class SyncApi(private val cfg: SyncConfig, private val client: OkHttpClient = defaultClient()) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class PushResult(val id: Long, val puntiPresenti: Int)
    data class PullPage(
        val sentieri: List<SentieroDto>, val poi: List<PoiDto>, val foto: List<FotoDto>,
        val until: Long, val more: Boolean
    )

    private fun apiGet(path: String, vararg q: Pair<String, String>): Request {
        val url = "${cfg.base}/$path".toHttpUrl().newBuilder().apply {
            q.forEach { addQueryParameter(it.first, it.second) }
        }.build()
        val builder = Request.Builder().url(url).get()
        if (cfg.token.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${cfg.token}")
        }
        return builder.build()
    }

    private fun apiPost(path: String, body: JSONObject): Request {
        val builder = Request.Builder().url("${cfg.base}/$path")
            .post(body.toString().toRequestBody(jsonType))
        if (cfg.token.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${cfg.token}")
        }
        return builder.build()
    }

    private suspend fun <T> call(req: Request, handle: (Response) -> T): T =
        withContext(Dispatchers.IO) { client.newCall(req).execute().use(handle) }

    private fun jsonOrThrow(r: Response): JSONObject {
        val txt = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw SyncHttpException(r.code, "HTTP ${r.code} ${txt.take(160)}")
        return JSONObject(txt)
    }

    suspend fun pushSentiero(s: SentieroDto, poi: List<PoiDto>, foto: List<FotoDto>): PushResult {
        val body = JSONObject()
            .put("sentiero", s.toJson())
            .put("poi", JSONArray(poi.map { it.toJson() }))
            .put("foto", JSONArray(foto.map { it.toJson() }))
        Log.d("Sync", "pushSentiero ")
        return call(apiPost("sync-push.php", body)) { r ->
            val o = jsonOrThrow(r)
            PushResult(o.optLong("id"), o.optInt("puntiPresenti"))
        }
    }

    suspend fun pushPunti(uuid: String, offset: Int, punti: List<PuntoDto>): Int {
        val body = JSONObject().put("uuid", uuid).put("offset", offset)
            .put("punti", JSONArray(punti.map { it.toJson() }))
        return call(apiPost("sync-points.php", body)) { r ->
            val txt = r.body?.string().orEmpty()
            Log.d("Sync", "pushPunti ")
            if (r.code != 409 && !r.isSuccessful) throw SyncHttpException(r.code, "HTTP ${r.code} ${txt.take(160)}")
            JSONObject(txt).getInt("puntiPresenti")
        }
    }

    suspend fun pull(since: Long): PullPage =
        call(apiGet("sync-pull.php", "since" to since.toString())) { r ->
            val o = jsonOrThrow(r)
            PullPage(
                o.getJSONArray("sentieri").map { SentieroDto.fromJson(it) },
                o.getJSONArray("poi").map { PoiDto.fromJson(it) },
                o.getJSONArray("foto").map { FotoDto.fromJson(it) },
                o.getLong("until"), o.optBoolean("more")
            )
        }

    suspend fun trackPoints(uuid: String, offset: Int, limit: Int = 1000): List<PuntoDto> =
        call(apiGet("track-points.php", "uuid" to uuid, "offset" to offset.toString(), "limit" to limit.toString())) { r ->
            jsonOrThrow(r).getJSONArray("punti").map { PuntoDto.fromJson(it) }
        }

    suspend fun caricaFoto(uuid: String, file: File) = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("${cfg.base}/files/Foto/$uuid.jpg")
            .header("X-Content-SHA256", sha256(file))
            .put(file.asRequestBody("image/jpeg".toMediaType()))
        if (cfg.token.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${cfg.token}")
        }
        val req = builder.build()
        client.newCall(req).execute().use { r ->
            if (r.code != 200 && r.code != 201) throw SyncHttpException(r.code, "upload foto $uuid: HTTP ${r.code}")
        }
    }

    suspend fun scaricaFoto(uuid: String, dest: File) = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/files/api/file".toHttpUrl().newBuilder().addQueryParameter("path", "Foto/$uuid.jpg").build()
        val builder = Request.Builder().url(url).get()
        if (cfg.token.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${cfg.token}")
        }
        val req = builder.build()
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SyncHttpException(r.code, "download foto $uuid: HTTP ${r.code}")
            r.body!!.byteStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IOException("impossibile salvare ${dest.name}")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

private fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { s ->
        val b = ByteArray(8192)
        while (true) {
            val n = s.read(b)
            if (n < 0) break
            md.update(b, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

// ───────────────────────── Logica di sincronizzazione ─────────────────────────

class SyncManager(
    private val context: Context,
    private val sentieriDao: SentieriDao,
    private val trackDao: TrackDao,
    private val poiDao: PoiDao,
    private val fotoPoiDao: FotoPoiDao
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private fun getStandardPhotoDir(): File {
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val sentieri = File(dcim, "Sentieri")
        if (!sentieri.exists()) sentieri.mkdirs()
        return sentieri
    }

    private fun getApi(): SyncApi {
        //val serverUrl = prefs.getString("nas_server_url", "https://sentieriap.duckdns.org/files")?.trimEnd('/') ?: ""
        val serverUrl = ServerConfig.filesUrl(prefs)
        val token = prefs.getString("nas_auth_token", "")?.trim() ?: ""
        if (serverUrl.isEmpty()) {
            throw IOException("URL Server NAS non configurato nelle impostazioni")
        }
        return SyncApi(SyncConfig(serverUrl, token))
    }

    suspend fun sync(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Inizializzazione sincronizzazione HTTP...")
            val api = getApi()

            onProgress("Verifica percorsi da inviare...")
            invia(api, onProgress)

            onProgress("Ricezione aggiornamenti dal server...")
            ricevi(api, onProgress)

            onProgress("Sincronizzazione completata con successo")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la sincronizzazione", e)
            Result.failure(e)
        }
    }

    private suspend fun invia(api: SyncApi, onProgress: (String) -> Unit) {
        val allSentieri = sentieriDao.getTuttiSentieriList()
        val syncedPrefs = context.getSharedPreferences(PREFS_SYNCED_TRACKS, Context.MODE_PRIVATE)

        for (sentiero in allSentieri) {
            val lastSynced = syncedPrefs.getLong(sentiero.uuid, 0L)
            val pois = poiDao.getPoisByTrackUuid(sentiero.uuid)
            val fotos = fotoPoiDao.getFotosByTrackUuid(sentiero.uuid)

            val maxLocalUpdate = maxOf(
                sentiero.lastUpdate,
                pois.maxOfOrNull { it.lastUpdate } ?: 0L,
                fotos.maxOfOrNull { it.lastUpdate } ?: 0L
            )

            // Se il percorso non è mai stato inviato o ha aggiornamenti più recenti
            if (maxLocalUpdate > lastSynced) {
                onProgress("Caricamento: ${sentiero.nome}")

                // 1. Upload delle foto fisiche
                val fotoDtos = mutableListOf<FotoDto>()
                for (f in fotos) {
                    val file = risolviFileFoto(f.uriPath)
                    if (file != null && file.exists()) {
                        try {
                            onProgress("Upload foto: ${f.nomeFoto}")
                            api.caricaFoto(f.uuid, file)
                            fotoDtos.add(FotoDto.fromEntity(f))
                        } catch (e: Exception) {
                            Log.w(TAG, "Errore upload foto ${f.nomeFoto}: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "File foto non trovato localmente: ${f.uriPath}")
                    }
                }

                val poiDtos = pois.map { PoiDto.fromEntity(it) }
                val sentieroDto = SentieroDto.fromEntity(sentiero)

                // 2. Push metadati sentiero, poi e foto
                val pushResult = api.pushSentiero(sentieroDto, poiDtos, fotoDtos)

                // 3. Invio punti a blocchi
                var presenti = pushResult.puntiPresenti
                val totalePunti = trackDao.countPointsByTrackUuid(sentiero.uuid)
                var disallineamenti = 0

                while (presenti < totalePunti) {
                    val blocco = trackDao.getPointsByTrackUuidPaged(sentiero.uuid, presenti, CHUNK)
                    if (blocco.isEmpty()) break

                    val bloccoDto = blocco.map { PuntoDto.fromEntity(it) }
                    val dopo = api.pushPunti(sentiero.uuid, presenti, bloccoDto)

                    if (dopo == presenti + blocco.size) {
                        disallineamenti = 0
                    } else if (dopo == presenti || ++disallineamenti > 3) {
                        throw IOException("Offset punti non allineato (server=$dopo, locale=$presenti)")
                    }
                    presenti = dopo
                }

                if (presenti >= totalePunti) {
                    syncedPrefs.edit { putLong(sentiero.uuid, maxLocalUpdate) }
                    onProgress("Aggiornato sul NAS: ${sentiero.nome}")
                }
            }
        }
    }

    private suspend fun ricevi(api: SyncApi, onProgress: (String) -> Unit) {
        var since = prefs.getLong(KEY_SINCE, 0L)

        while (true) {
            val page = api.pull(since)

            for (sDto in page.sentieri) {
                val existing = sentieriDao.getByUuid(sDto.uuid)
                if (existing == null || sDto.lastUpdate > existing.lastUpdate) {
                    if (existing == null) {
                        onProgress("Scaricamento: ${sDto.nome}")
                    } else {
                        onProgress("Aggiornamento locale: ${sDto.nome}")
                    }
                    val sentieroEntity = Sentieri(
                        id = existing?.id ?: 0,
                        nome = sDto.nome,
                        descrizione = sDto.descrizione,
                        lunghezza = sDto.lunghezza,
                        dislivello = sDto.dislivello,
                        discesa = sDto.discesa,
                        HrMed = sDto.hrMed,
                        HrMax = sDto.hrMax,
                        DataOra = sDto.dataOra,
                        TempMedia = sDto.tempMedia,
                        TempMax = sDto.tempMax,
                        TempMin = sDto.tempMin,
                        DataFine = sDto.dataFine,
                        TempoTot = sDto.tempoTot,
                        TempoInMov = sDto.tempoInMov,
                        MediaVel = sDto.mediaVel,
                        uuid = sDto.uuid,
                        lastUpdate = sDto.lastUpdate
                    )
                    sentieriDao.upsert(sentieroEntity)

                    // Scarica i punti del percorso
                    scaricaPuntiPercorso(api, sDto)
                }
            }

            for (pDto in page.poi) {
                val sentieroLocale = sentieriDao.getByUuid(pDto.trackUuid)
                val existingPoi = poiDao.getByUuid(pDto.uuid)
                if (existingPoi == null || pDto.lastUpdate > existingPoi.lastUpdate) {
                    val poiEntity = PoiDB(
                        Id = existingPoi?.Id ?: 0,
                        Trackid = sentieroLocale?.id ?: 0,
                        Latit = pDto.lat,
                        Longit = pDto.lon,
                        Ele = pDto.ele,
                        NomePOI = pDto.nome,
                        DescrPOI = pDto.descr,
                        UriPath = existingPoi?.UriPath ?: pDto.uriPath,
                        Time = pDto.time,
                        uuid = pDto.uuid,
                        trackUuid = pDto.trackUuid,
                        lastUpdate = pDto.lastUpdate
                    )
                    poiDao.upsert(poiEntity)
                }
            }

            for (fDto in page.foto) {
                val sentieroLocale = sentieriDao.getByUuid(fDto.trackUuid)
                val existingFoto = fotoPoiDao.getByUuid(fDto.uuid)
                if (existingFoto == null || fDto.lastUpdate > existingFoto.lastUpdate) {
                    val photoDir = getStandardPhotoDir()
                    val targetFile = File(photoDir, fDto.nomeFoto)

                    val fotoEntity = FotoPoi(
                        id = existingFoto?.id ?: 0,
                        trackid = sentieroLocale?.id ?: 0,
                        uriPath = targetFile.absolutePath,
                        nomeFoto = fDto.nomeFoto,
                        uuid = fDto.uuid,
                        trackUuid = fDto.trackUuid,
                        lastUpdate = fDto.lastUpdate
                    )
                    fotoPoiDao.upsert(fotoEntity)

                    // Download del file JPEG se mancante o vuoto
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        try {
                            onProgress("Download foto: ${fDto.nomeFoto}")
                            api.scaricaFoto(fDto.uuid, targetFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "Download foto fallito ${fDto.nomeFoto}: ${e.message}")
                        }
                    }
                }
            }

            since = page.until
            prefs.edit { putLong(KEY_SINCE, since) }
            if (!page.more) break
        }
    }

    private suspend fun scaricaPuntiPercorso(api: SyncApi, s: SentieroDto) {
        val sentieroLocale = sentieriDao.getByUuid(s.uuid) ?: return
        var locali = trackDao.countPointsByTrackUuid(s.uuid)

        while (locali < s.nPunti) {
            val blocco = api.trackPoints(s.uuid, locali, CHUNK)
            if (blocco.isEmpty()) break

            val trackPoints = blocco.map { p ->
                Track(
                    Id = 0,
                    Trackid = sentieroLocale.id,
                    Latit = p.lat.toFloat(),
                    Longit = p.lon.toFloat(),
                    Ele = p.ele.toFloat(),
                    Ora = p.time,
                    trackUuid = s.uuid,
                    batteryPercent = p.batteryPercent,
                    cadence = p.cadence,
                    assistLevel = p.assistLevel,
                    power = p.power,
                    heartRate = p.heartRate
                )
            }
            trackDao.upsertPoints(trackPoints)
            locali += blocco.size
        }
    }

    private fun risolviFileFoto(uriPath: String): File? {
        if (uriPath.isEmpty()) return null
        val uri = Uri.parse(uriPath)
        if (uri.scheme == "content") {
            try {
                val fileName = MapUtils.getFileNameFromUri(context, uri)
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                return tempFile
            } catch (e: Exception) {
                Log.e(TAG, "Errore risoluzione file da Uri content: ${e.message}")
                return null
            }
        }
        val f = File(uriPath)
        return if (f.exists()) f else null
    }

    companion object {
        private const val CHUNK = 1000
        private const val KEY_SINCE = "sync_http_since"
        private const val PREFS_SYNCED_TRACKS = "sync_http_tracks"
    }
}
