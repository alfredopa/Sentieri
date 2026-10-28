package com.apstudio.sentieri.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.BuildConfig
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDao
import com.apstudio.sentieri.db.Track
import com.apstudio.sentieri.db.TrackDao
import com.apstudio.sentieri.sync.DownloadService.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
            time = e.Time, uriPath = e.UriPath.substringAfterLast('/'),
            lastUpdate = e.lastUpdate
        )
    }
}

data class FotoDto(
    val uuid: String, val trackUuid: String, val nomeFoto: String,
    val uriPath: String = "", val lastUpdate: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid)
        .put("trackUuid", trackUuid)
        .put("NomeFoto", nomeFoto)
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

    data class SyncIndexItem(
        val lastUpdate: Long,
        val nPunti: Int,
        val nPoi: Int = 0,
        val nFoto: Int = 0
    )

    data class PushResult(val id: Long, val puntiPresenti: Int, val lastUpdate: Long = 0L)
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

    suspend fun pushSentiero(s: SentieroDto, poi: List<PoiDto>): PushResult {
        val body = JSONObject()
            .put("sentiero", s.toJson())
            .put("poi", JSONArray(poi.map { it.toJson() }))
        Log.d("Sync", "pushSentiero ")
        return call(apiPost("sync-push.php", body)) { r ->
            val o = jsonOrThrow(r)
            PushResult(
                id = o.optLong("id"),
                puntiPresenti = o.optInt("puntiPresenti"),
                lastUpdate = o.optLong("lastUpdate", 0L)
            )
        }
    }

    suspend fun syncIndex(): Map<String, SyncIndexItem> = call(apiGet("sync-index.php")) { r ->
        val o = jsonOrThrow(r)
        val idxObj = o.optJSONObject("indice") ?: JSONObject()
        val result = mutableMapOf<String, SyncIndexItem>()
        val keys = idxObj.keys()
        while (keys.hasNext()) {
            val uuid = keys.next()
            val itemObj = idxObj.getJSONObject(uuid)
            result[uuid] = SyncIndexItem(
                lastUpdate = itemObj.optLong("lastUpdate", 0L),
                nPunti = itemObj.optInt("nPunti", 0),
                nPoi = itemObj.optInt("nPoi", 0),
                nFoto = itemObj.optInt("nFoto", 0)
            )
        }
        result
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

    suspend fun pushFoto(f: FotoDto) {
        call(apiPost("sync-foto.php", f.toJson())) { r -> jsonOrThrow(r); Unit }
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

    suspend fun caricaFoto(nome: String, inputStream: java.io.InputStream, length: Long, sha256: String) = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/files".toHttpUrl().newBuilder()
            .addPathSegment("Foto").addPathSegment(nome).build()

        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType() = "image/jpeg".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: okio.BufferedSink) {
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("X-Content-SHA256", sha256)
            .put(requestBody).build()

        client.newCall(req).execute().use { r ->
            if (r.code != 200 && r.code != 201) throw SyncHttpException(r.code, "upload foto $nome: HTTP ${r.code}")
        }
    }


    suspend fun scaricaFoto(nomeFile: String, dest: File) = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/files/api/file".toHttpUrl().newBuilder().addQueryParameter("path", "Foto/$nomeFile").build()
        val builder = Request.Builder().url(url).get()
        if (cfg.token.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${cfg.token}")
        }
        val req = builder.build()
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SyncHttpException(r.code, "download foto $nomeFile: HTTP ${r.code}")
            r.body!!.byteStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IOException("impossibile salvare ${dest.name}")
        }
    }

    suspend fun caricaVoce(nome: String, inputStream: java.io.InputStream, length: Long, sha256: String) = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/files".toHttpUrl().newBuilder()
            .addPathSegment("VoceWaypoint").addPathSegment(nome).build()

        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType() = "audio/m4a".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: okio.BufferedSink) {
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("X-Content-SHA256", sha256)
            .put(requestBody).build()

        client.newCall(req).execute().use { r ->
            if (r.code != 200 && r.code != 201) throw SyncHttpException(r.code, "upload voce $nome: HTTP ${r.code}")
        }
    }

    suspend fun scaricaVoce(nomeFile: String, dest: File) = withContext(Dispatchers.IO) {
        val url = "${cfg.base}/files/api/file".toHttpUrl().newBuilder().addQueryParameter("path", "VoceWaypoint/$nomeFile").build()
        val builder = Request.Builder().url(url).get()
        if (cfg.token.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${cfg.token}")
        }
        val req = builder.build()
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SyncHttpException(r.code, "download voce $nomeFile: HTTP ${r.code}")
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

// ───────────────────────── Logica di sincronizzazione ─────────────────────────

private data class ServerCatalog(
    val sentieri: Map<String, SentieroDto>,
    val pois: List<PoiDto>,
    val fotos: List<FotoDto>
)

private fun normalizeSec(ts: Long): Long = if (ts > 10_000_000_000L) ts / 1000L else ts

class SyncManager(
    private val context: Context,
    private val sentieriDao: SentieriDao,
    private val trackDao: TrackDao,
    private val poiDao: PoiDao,
    private val fotoPoiDao: FotoPoiDao
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private fun getStandardPhotoDir(): File {
        val appMediaDir = context.externalMediaDirs.firstOrNull()
            ?: context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.cacheDir
        val sentieri = File(appMediaDir, "Sentieri")
        if (!sentieri.exists()) sentieri.mkdirs()
        return sentieri
    }

    private fun getStandardVoceDir(): File {
        @Suppress("DEPRECATION")
        val mediaDir = context.externalMediaDirs.firstOrNull()
            ?: context.getExternalFilesDir(null)
            ?: context.cacheDir
        val targetDir = File(mediaDir, "VoceWaypoint")
        if (!targetDir.exists()) targetDir.mkdirs()
        return targetDir
    }

    private fun risolviFileAudio(uriPath: String): File? {
        if (uriPath.isBlank()) return null

        val fileDiretto = File(uriPath)
        if (!uriPath.startsWith("content://") && fileDiretto.isAbsolute && fileDiretto.exists() && fileDiretto.canRead()) {
            return fileDiretto
        }

        val fileName = if (uriPath.contains("/")) uriPath.substringAfterLast("/") else uriPath

        // 1. Cerca nella cartella VoceWaypoint
        val fileInMedia = File(getStandardVoceDir(), fileName)
        if (fileInMedia.exists() && fileInMedia.canRead()) {
            return fileInMedia
        }

        // 2. Fallback alla vecchia cartella VoiceNotesWaypoints
        val legacyDir = context.getExternalFilesDir("VoiceNotesWaypoint")
        val fileInLegacy = legacyDir?.let { File(it, fileName) }
        if (fileInLegacy?.exists() == true && fileInLegacy.canRead()) {
            return fileInLegacy
        }

        return if (fileDiretto.exists() && fileDiretto.canRead()) fileDiretto else null
    }

    private fun getApi(): SyncApi {
        val serverUrl = ServerConfig.filesUrl(prefs)
        var token = prefs.getString("nas_auth_token", "")?.trim() ?: ""
        if (token.isEmpty()) token = BuildConfig.NAS_AUTH_TOKEN
        if (serverUrl.isEmpty()) {
            throw IOException("URL Server NAS non configurato nelle impostazioni")
        }
        return SyncApi(SyncConfig(serverUrl, token))
    }

    suspend fun sync(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Connessione al server NAS...")
            val api = getApi()

            onProgress("Lettura indice dal NAS...")
            val indexMap = api.syncIndex()
            Log.i(TAG, "Sincronizzazione avviata: Lettore indice NAS con ${indexMap.size} percorsi.")

            val allSentieri = sentieriDao.getTuttiSentieriList()

            // 1. Identifica percorsi locali che necessitano di Upload
            val toUpload = mutableListOf<Sentieri>()
            var inviatiCount = 0
            var scaricatiCount = 0
            var invariatiCount = 0

            for (s in allSentieri) {
                val idxItem = indexMap[s.uuid]
                val needsPush = (idxItem == null)

                /*val pois = poiDao.getPoisByTrackUuid(s.uuid)
                val fotos = fotoPoiDao.getFotosByTrackUuid(s.uuid)
                val totalePunti = trackDao.countPointsByTrackUuid(s.uuid)

                val maxLocalUpdate = maxOf(
                    s.lastUpdate,
                    pois.maxOfOrNull { it.lastUpdate } ?: 0L,
                    fotos.maxOfOrNull { it.lastUpdate } ?: 0L
                )

                val idxItem = indexMap[s.uuid]
                val localSec = normalizeSec(maxLocalUpdate)
                val serverSec = if (idxItem != null) normalizeSec(idxItem.lastUpdate) else 0L

                val needsPush = (idxItem == null) ||
                                (localSec > serverSec + 1) ||
                                (totalePunti > idxItem.nPunti)*/

                if (needsPush) {
                    toUpload.add(s)
                } else {
                    invariatiCount++
                }
            }
            Log.d(TAG, " Sincronizzazione 1: toUpload=${toUpload.size},  invariati=$invariatiCount")
            // Identifica UUID presenti nel NAS che necessitano di Download/Aggiornamento locale
            val toDownloadUuids = mutableSetOf<String>()
            for ((uuid, idxItem) in indexMap) {
                val existing = sentieriDao.getByUuid(uuid)
                if (existing == null) {
                    toDownloadUuids.add(uuid)
                } /*else {
                    val serverSec = normalizeSec(idxItem.lastUpdate)
                    val localSec = normalizeSec(existing.lastUpdate)
                    val puntiLocali = trackDao.countPointsByTrackUuid(uuid)
                    if (serverSec > localSec + 1 || puntiLocali < idxItem.nPunti) {
                        toDownloadUuids.add(uuid)
                    }
                }*/
            }

            Log.d(TAG, "Delta Sincronizzazione: toUpload=${toUpload.size}, toDownload=${toDownloadUuids.size}, invariati=$invariatiCount")

            if (toUpload.isEmpty() && toDownloadUuids.isEmpty()) {
                onProgress("✓ Tutti i $invariatiCount percorsi sono già sincronizzati con il NAS")
                return@withContext Result.success(Unit)
            }

            // 2. Esegui INVI / UPLOADS
            for (sentiero in toUpload) {
                val pois = poiDao.getPoisByTrackUuid(sentiero.uuid)
                val fotos = fotoPoiDao.getFotosByTrackUuid(sentiero.uuid)
                val totalePunti = trackDao.countPointsByTrackUuid(sentiero.uuid)
                val idxItem = indexMap[sentiero.uuid]

                onProgress("⬆ [INVIO]\nTraccia: ${sentiero.nome}")

                val poiDtos = pois.map { PoiDto.fromEntity(it) }
                val sentieroDto = SentieroDto.fromEntity(sentiero)

                val pushResult = api.pushSentiero(sentieroDto, poiDtos)
                var presenti = pushResult.puntiPresenti

                if (presenti < totalePunti) {
                    var disallineamenti = 0
                    while (presenti < totalePunti) {
                        onProgress("Punti: $presenti / $totalePunti")
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
                }

                // Upload delle foto (ora che il sentiero esiste sul server)
                for (f in fotos) {
                    try {
                        val uri = if (f.uriPath.startsWith("content://")) {
                            Uri.parse(f.uriPath)
                        } else {
                            val cleanPath = f.uriPath.removePrefix("file://")
                            val fileName = cleanPath.substringAfterLast('/')
                            val projection = arrayOf(MediaStore.Images.Media._ID)
                            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                            val selectionArgs = arrayOf(fileName)

                            context.contentResolver.query(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                selection,
                                selectionArgs,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                                } else null
                            } ?: Uri.fromFile(File(f.uriPath))
                        }

                        val (sha256, length) = context.contentResolver.openInputStream(uri)?.use { input ->
                            val md = MessageDigest.getInstance("SHA-256")
                            var bytesRead = 0L
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                md.update(buffer, 0, read)
                                bytesRead += read
                            }
                            md.digest().joinToString("") { "%02x".format(it) } to bytesRead
                        } ?: throw IOException("Impossibile leggere il file per calcolare SHA-256")

                        context.contentResolver.openInputStream(uri)?.use { uploadStream ->
                            api.caricaFoto(f.nomeFoto, uploadStream, length, sha256)
                            api.pushFoto(FotoDto.fromEntity(f))
                            Log.w(TAG, "carica foto ${f.nomeFoto} completata")
                        } ?: Log.w(TAG, "Impossibile aprire stream per upload di ${f.nomeFoto}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Errore upload foto ${f.nomeFoto}: ${e.message}")
                    }
                }

                // Upload dei file audio dei POI / Waypoint
                for (p in pois) {
                    if (p.UriPath.isNotBlank()) {
                        val audioFile = risolviFileAudio(p.UriPath)
                        if (audioFile != null && audioFile.exists()) {
                            try {
                                val fileName = audioFile.name
                                val length = audioFile.length()
                                val sha256 = audioFile.inputStream().use { input ->
                                    val md = MessageDigest.getInstance("SHA-256")
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        md.update(buffer, 0, read)
                                    }
                                    md.digest().joinToString("") { "%02x".format(it) }
                                }

                                audioFile.inputStream().use { uploadStream ->
                                    api.caricaVoce(fileName, uploadStream, length, sha256)
                                    Log.w(TAG, "carica voce $fileName completata")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Errore upload audio ${p.UriPath}: ${e.message}")
                            }
                        }
                    }
                }

                // Allinea il timestamp locale
                val updatedServerTs = if (pushResult.lastUpdate > 0L) {
                    pushResult.lastUpdate
                } else if (idxItem != null) {
                    idxItem.lastUpdate
                } else {
                    System.currentTimeMillis()
                }
                val finalLocalMs = if (updatedServerTs < 10_000_000_000L) updatedServerTs * 1000L else updatedServerTs

                sentiero.lastUpdate = finalLocalMs
                sentieriDao.upsert(sentiero)

                for (p in pois) {
                    p.lastUpdate = finalLocalMs
                    poiDao.upsert(p)
                }
                for (f in fotos) {
                    f.lastUpdate = finalLocalMs
                    fotoPoiDao.upsert(f)
                }

                inviatiCount++
            }

            // 3. Esegui RICEZIONE / DOWNLOADS
            if (toDownloadUuids.isNotEmpty()) {
                onProgress("⬇ [RICEZIONE]\nLettura dati dal NAS...")
                val catalog = fetchServerCatalog(api)

                for (sDto in catalog.sentieri.values) {
                    if (!toDownloadUuids.contains(sDto.uuid)) continue

                    onProgress("⬇ [RICEZIONE]\nTraccia: ${sDto.nome}")

                    val existing = sentieriDao.getByUuid(sDto.uuid)
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

                    val puntiLocali = if (existing != null) trackDao.countPointsByTrackUuid(sDto.uuid) else 0
                    if (puntiLocali < sDto.nPunti) {
                        scaricaPuntiPercorso(api, sDto, onProgress)
                    }

                    scaricatiCount++
                }

                for (pDto in catalog.pois) {
                    if (!toDownloadUuids.contains(pDto.trackUuid)) continue
                    val sentieroLocale = sentieriDao.getByUuid(pDto.trackUuid)
                    val existingPoi = poiDao.getByUuid(pDto.uuid)

                    val audioFileName = if (pDto.uriPath.contains("/")) pDto.uriPath.substringAfterLast("/") else pDto.uriPath
                    val hasAudio = audioFileName.isNotBlank() && (audioFileName.endsWith(".m4a", true) || audioFileName.endsWith(".mp3", true) || audioFileName.endsWith(".aac", true) || audioFileName.endsWith(".3gp", true) || audioFileName.startsWith("AUD_"))
                    val finalUriPath = if (hasAudio) audioFileName else (existingPoi?.UriPath ?: pDto.uriPath)

                    val poiEntity = PoiDB(
                        Id = existingPoi?.Id ?: 0,
                        Trackid = sentieroLocale?.id ?: 0,
                        Latit = pDto.lat,
                        Longit = pDto.lon,
                        Ele = pDto.ele,
                        NomePOI = pDto.nome,
                        DescrPOI = pDto.descr,
                        UriPath = finalUriPath,
                        Time = pDto.time,
                        uuid = pDto.uuid,
                        trackUuid = pDto.trackUuid,
                        lastUpdate = pDto.lastUpdate
                    )
                    poiDao.upsert(poiEntity)

                    if (hasAudio) {
                        val targetAudioFile = File(getStandardVoceDir(), audioFileName)
                        if (!targetAudioFile.exists() || targetAudioFile.length() == 0L) {
                            try {
                                api.scaricaVoce(audioFileName, targetAudioFile)
                            } catch (e: Exception) {
                                Log.w(TAG, "Download audio fallito $audioFileName: ${e.message}")
                            }
                        }
                    }
                }

                for (fDto in catalog.fotos) {
                    if (!toDownloadUuids.contains(fDto.trackUuid)) continue
                    val sentieroLocale = sentieriDao.getByUuid(fDto.trackUuid)
                    val existingFoto = fotoPoiDao.getByUuid(fDto.uuid)
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

                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        try {
                            api.scaricaFoto(fDto.nomeFoto, targetFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "Download foto fallito ${fDto.nomeFoto}: ${e.message}")
                        }
                    }
                }
            }

            val summary = StringBuilder("✓ Sincronizzazione completata\n\n")
            if (inviatiCount > 0) summary.append(" Inviati sul NAS: $inviatiCount percorsi\n")
            if (scaricatiCount > 0) summary.append(" Scaricati dal NAS: $scaricatiCount percorsi\n")
            summary.append(" Invariati: $invariatiCount percorsi")

            onProgress(summary.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la sincronizzazione", e)
            Result.failure(e)
        }
    }

    /**
     * Scarica l'intero database dal server NAS (es. per un nuovo telefono o ripristino).
     * Esegue il pull a partire da since = 0L.
     * Per i percorsi non presenti in locale scarica anche tutti i punti traccia.
     */
    suspend fun scaricaDatabaseCompleto(onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Inizializzazione download database NAS...")
            val api = getApi()

            onProgress("Recupero catalogo completo dal server NAS...")
            val catalog = fetchServerCatalog(api)
            Log.i(TAG, "Download completo avviato: ${catalog.sentieri.size} percorsi presenti sul server.")

            onProgress("Scaricamento di tutti i percorsi dal server...")
            ricevi(api, catalog, onProgress)

            onProgress("Download database completato con successo")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il download del database", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchServerCatalog(api: SyncApi): ServerCatalog {
        val sentieriMap = mutableMapOf<String, SentieroDto>()
        val poisList = mutableListOf<PoiDto>()
        val fotosList = mutableListOf<FotoDto>()
        var since = 0L
        while (true) {
            val page = api.pull(since)
            for (s in page.sentieri) {
                sentieriMap[s.uuid] = s
            }
            poisList.addAll(page.poi)
            fotosList.addAll(page.foto)
            since = page.until
            if (!page.more) break
        }
        return ServerCatalog(sentieriMap, poisList, fotosList)
    }

    private suspend fun ricevi(api: SyncApi, catalog: ServerCatalog, onProgress: (String) -> Unit) {
        var scaricatiCount = 0

        for (sDto in catalog.sentieri.values) {
            val existing = sentieriDao.getByUuid(sDto.uuid)
            val isNuovoInLocale = (existing == null)
            val serverSec = normalizeSec(sDto.lastUpdate)
            val localSec = if (existing != null) normalizeSec(existing.lastUpdate) else 0L

            val isModificatoSulServer = (existing != null && serverSec > localSec + 1)
            val puntiLocali = if (existing != null) trackDao.countPointsByTrackUuid(sDto.uuid) else 0
            val puntiMancantiInLocale = (existing != null && puntiLocali < sDto.nPunti)

            if (isNuovoInLocale) {
                Log.i(TAG, "ricevi: Sentiero '${sDto.nome}' (uuid=${sDto.uuid}) MANCANTE in locale. Scaricamento dal server...")
                onProgress("⬇ [RICEZIONE]\nTraccia: ${sDto.nome}")
            } else if (isModificatoSulServer) {
                Log.i(TAG, "ricevi: Sentiero '${sDto.nome}' modificato sul server (serverSec=$serverSec vs localSec=$localSec). Aggiornamento locale...")
                onProgress("⬇ [RICEZIONE]\nTraccia: ${sDto.nome}")
            } else if (puntiMancantiInLocale) {
                Log.i(TAG, "ricevi: Sentiero '${sDto.nome}' ha punti mancanti in locale ($puntiLocali/$sDto.nPunti). Scaricamento punti...")
                onProgress("⬇ [RICEZIONE]\nTraccia: ${sDto.nome}")
            } else {
                Log.d(TAG, "ricevi: Sentiero '${sDto.nome}' già presente e aggiornato in locale. Saltato.")
                continue
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

            // Scarica i punti del percorso se mancanti
            if (puntiLocali < sDto.nPunti) {
                scaricaPuntiPercorso(api, sDto, onProgress)
            }

            scaricatiCount++
        }

        // Processa POI dal catalogo server
        for (pDto in catalog.pois) {
            val sentieroLocale = sentieriDao.getByUuid(pDto.trackUuid)
            val existingPoi = poiDao.getByUuid(pDto.uuid)
            val serverSec = normalizeSec(pDto.lastUpdate)
            val localSec = if (existingPoi != null) normalizeSec(existingPoi.lastUpdate) else 0L

            if (existingPoi == null || serverSec > localSec + 1) {
                val audioFileName = if (pDto.uriPath.contains("/")) pDto.uriPath.substringAfterLast("/") else pDto.uriPath
                val hasAudio = audioFileName.isNotBlank() && (audioFileName.endsWith(".m4a", true) || audioFileName.endsWith(".mp3", true) || audioFileName.endsWith(".aac", true) || audioFileName.endsWith(".3gp", true) || audioFileName.startsWith("AUD_"))
                val finalUriPath = if (hasAudio) audioFileName else (existingPoi?.UriPath ?: pDto.uriPath)

                val poiEntity = PoiDB(
                    Id = existingPoi?.Id ?: 0,
                    Trackid = sentieroLocale?.id ?: 0,
                    Latit = pDto.lat,
                    Longit = pDto.lon,
                    Ele = pDto.ele,
                    NomePOI = pDto.nome,
                    DescrPOI = pDto.descr,
                    UriPath = finalUriPath,
                    Time = pDto.time,
                    uuid = pDto.uuid,
                    trackUuid = pDto.trackUuid,
                    lastUpdate = pDto.lastUpdate
                )
                poiDao.upsert(poiEntity)

                if (hasAudio) {
                    val targetAudioFile = File(getStandardVoceDir(), audioFileName)
                    if (!targetAudioFile.exists() || targetAudioFile.length() == 0L) {
                        try {
                            onProgress("⬇ [RICEZIONE]\nDownload audio: $audioFileName")
                            api.scaricaVoce(audioFileName, targetAudioFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "Download audio fallito $audioFileName: ${e.message}")
                        }
                    }
                }
            }
        }

        // Processa Foto dal catalogo server
        for (fDto in catalog.fotos) {
            val sentieroLocale = sentieriDao.getByUuid(fDto.trackUuid)
            val existingFoto = fotoPoiDao.getByUuid(fDto.uuid)
            val serverSec = normalizeSec(fDto.lastUpdate)
            val localSec = if (existingFoto != null) normalizeSec(existingFoto.lastUpdate) else 0L

            if (existingFoto == null || serverSec > localSec + 1) {
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

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    try {
                        onProgress("⬇ [RICEZIONE]\nDownload foto: ${fDto.nomeFoto}")
                        api.scaricaFoto(fDto.uuid, targetFile)
                    } catch (e: Exception) {
                        Log.w(TAG, "Download foto fallito ${fDto.nomeFoto}: ${e.message}")
                    }
                }
            }
        }

        Log.i(TAG, "ricevi: Completato. Scaricati/aggiornati $scaricatiCount percorsi dal server.")
    }

    private suspend fun scaricaPuntiPercorso(api: SyncApi, s: SentieroDto, onProgress: (String) -> Unit) {
        val sentieroLocale = sentieriDao.getByUuid(s.uuid) ?: return
        var locali = trackDao.countPointsByTrackUuid(s.uuid)

        Log.i(TAG, "scaricaPuntiPercorso: Avvio download punti per '${s.nome}' (locali=$locali, totali=${s.nPunti})")

        while (locali < s.nPunti) {
            onProgress("Punti: $locali / ${s.nPunti}")
            val blocco = api.trackPoints(s.uuid, locali, CHUNK)
            if (blocco.isEmpty()) {
                Log.w(TAG, "scaricaPuntiPercorso: Nessun punto restituito per offset $locali")
                break
            }

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
        Log.i(TAG, "scaricaPuntiPercorso: Completato per '${s.nome}'. Punti in locale: $locali")
    }

    private fun risolviFileFoto(uriPath: String): File? {
        if (uriPath.isEmpty()) return null

        val fileDiretto = File(uriPath)
        // Se il file esiste ed è direttamente leggibile dal processo dell'app
        if (!uriPath.startsWith("content://") && fileDiretto.exists() && fileDiretto.canRead()) {
            return fileDiretto
        }

        val uri = if (uriPath.startsWith("content://") || uriPath.startsWith("file://")) {
            Uri.parse(uriPath)
        } else {
            Uri.fromFile(fileDiretto)
        }

        // 1. Prova ad aprire tramite ContentResolver e copiare nella cache dell'app (esente da EACCES / Scoped Storage)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val fileName = if (uriPath.contains("/")) uriPath.substringAfterLast("/") else "temp_foto.jpg"
                val tempFile = File(context.cacheDir, fileName)
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    return tempFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tentativo lettura via ContentResolver per '$uriPath' fallito: ${e.message}")
        }

        // 2. Fallback MediaStore per file in /storage/emulated/0/DCIM/Sentieri/
        try {
            val fileName = uriPath.substringAfterLast("/")
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex >= 0) {
                        val id = cursor.getLong(idIndex)
                        val mediaStoreUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        context.contentResolver.openInputStream(mediaStoreUri)?.use { input ->
                            val tempFile = File(context.cacheDir, fileName)
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                            if (tempFile.exists() && tempFile.length() > 0L) {
                                return tempFile
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore risoluzione MediaStore per '$uriPath': ${e.message}")
        }

        return null
    }

    companion object {
        private const val CHUNK = 1000
    }
}
