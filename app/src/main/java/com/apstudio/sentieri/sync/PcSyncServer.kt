package com.apstudio.sentieri.sync

import android.content.Context
import android.util.Log
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

private const val TAG = "PcSyncServer"

class PcSyncServer(
    private val context: Context,
    private val port: Int = 8080,
    private val repository: SentieriRepo,
    private val onClientConnected: (clientInfo: String) -> Unit,
    private val onLogActivity: (message: String) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    private val executor = Executors.newFixedThreadPool(4)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server HTTP in ascolto sulla porta $port")
                onLogActivity("Server HTTP in ascolto sulla porta $port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handleClientSocket(socket) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Errore server HTTP", e)
                    onLogActivity("Errore server HTTP: ${e.localizedMessage}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            executor.shutdown()
            Log.d(TAG, "Server HTTP fermato")
            onLogActivity("Server HTTP fermato")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante arresto server HTTP", e)
        }
        serverSocket = null
    }

    private fun handleClientSocket(socket: Socket) {
        val startTime = System.currentTimeMillis()
        val clientIp = socket.inetAddress?.hostAddress ?: "Sconosciuto"
        Log.d(TAG, "Nuova connessione socket da $clientIp")
        onClientConnected("Connesso con PC: $clientIp")

        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uri = parts[1]
            Log.d(TAG, "Richiesta HTTP: $method $uri da $clientIp")

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "")
                return
            }

            runBlocking(Dispatchers.IO) {
                when {
                    // 1. GET /api/v1/info
                    uri.startsWith("/api/v1/info") || uri == "/info" -> {
                        onLogActivity("Richiesta INFO da $clientIp")
                        val sentieriList = repository.sentieriDao.getTuttiSentieriList()
                        val json = JSONObject().apply {
                            put("status", "ok")
                            put("app", "Sentieri")
                            put("version", "2.3.1")
                            put("sentieriCount", sentieriList.size)
                        }
                        Log.d(TAG, "Risposta INFO inviata: ${sentieriList.size} sentieri presenti")
                        sendResponse(output, 200, "OK", json.toString())
                    }

                    // 2. GET /api/v1/index (Indice compatto UUID + timestamps per delta-sync)
                    uri.startsWith("/api/v1/index") || uri == "/index" -> {
                        onLogActivity("Richiesta Indice Sync da $clientIp")
                        val sentieriList = repository.sentieriDao.getTuttiSentieriList()
                        val indexObj = JSONObject()
                        sentieriList.forEach { s ->
                            val item = JSONObject().apply {
                                put("lastUpdate", s.lastUpdate)
                                put("nome", s.nome)
                            }
                            indexObj.put(s.uuid, item)
                        }
                        val root = JSONObject().put("index", indexObj)
                        Log.d(TAG, "Risposta INDEX inviata per ${sentieriList.size} sentieri")
                        sendResponse(output, 200, "OK", root.toString())
                    }

                    // 3. GET /api/v1/sentieri (Metadata dei sentieri, ultra-veloce <10ms, NO full table scan)
                    uri.startsWith("/api/v1/sentieri") || uri.startsWith("/sentieri") -> {
                        onLogActivity("Richiesta Elenco Sentieri da $clientIp")
                        val sentieri = repository.sentieriDao.getTuttiSentieriList()
                        val jsonArray = JSONArray()
                        sentieri.forEach { s ->
                            val dto = SentieroDto.fromEntity(s)
                            jsonArray.put(dto.toJson())
                        }
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Inviati ${sentieri.size} sentieri a $clientIp in ${elapsed}ms")
                        onLogActivity("Inviati ${sentieri.size} sentieri a $clientIp (${elapsed}ms)")
                        sendResponse(output, 200, "OK", jsonArray.toString())
                    }

                    // 4. GET /api/v1/punti?uuid=... (Punti GPS per uno specifico sentiero UUID)
                    uri.startsWith("/api/v1/punti") || uri.startsWith("/punti") -> {
                        val uuid = extractQueryParam(uri, "uuid")
                        val nome = extractQueryParam(uri,"")
                        if (uuid.isBlank()) {
                            Log.w(TAG, "Richiesta punti fallita: 'uuid' mancante")
                            sendResponse(output, 400, "Bad Request", JSONObject().put("error", "Parametro 'uuid' mancante").toString())
                        } else {
                            val punti = repository.trackDao.getPointsByTrackUuid(uuid)
                            val jsonArray = JSONArray()
                            punti.forEach { p -> jsonArray.put(PuntoDto.fromEntity(p).toJson()) }
                            val elapsed = System.currentTimeMillis() - startTime
                            Log.d(TAG, "Inviati ${punti.size} punti per sentiero [$uuid] a $clientIp in ${elapsed}ms")
                            onLogActivity("Inviati ${punti.size} punti per sentiero [$uuid]")
                            sendResponse(output, 200, "OK", jsonArray.toString())
                        }
                    }

                    // 5. GET /api/v1/poi?uuid=...
                    uri.startsWith("/api/v1/poi") || uri.startsWith("/poi") -> {
                        val uuid = extractQueryParam(uri, "uuid")
                        val pois = if (uuid.isNotBlank()) repository.poiDao.getPoisByTrackUuid(uuid) else repository.poiDao.listPoiDB()
                        val jsonArray = JSONArray()
                        pois.forEach { p -> jsonArray.put(PoiDto.fromEntity(p).toJson()) }
                        Log.d(TAG, "Inviati ${pois.size} POI a $clientIp")
                        onLogActivity("Inviati ${pois.size} POI")
                        sendResponse(output, 200, "OK", jsonArray.toString())
                    }

                    // 6. GET /api/v1/foto?uuid=...
                    uri.startsWith("/api/v1/foto") || uri.startsWith("/foto") -> {
                        val uuid = extractQueryParam(uri, "uuid")
                        val fotos = if (uuid.isNotBlank()) repository.fotoPoiDao.getFotosByTrackUuid(uuid) else repository.fotoPoiDao.listFotoPoiDB()
                        val jsonArray = JSONArray()
                        fotos.forEach { f -> jsonArray.put(FotoDto.fromEntity(f).toJson()) }
                        Log.d(TAG, "Inviate info per ${fotos.size} foto a $clientIp")
                        onLogActivity("Inviati info per ${fotos.size} foto")
                        sendResponse(output, 200, "OK", jsonArray.toString())
                    }

                    // 7. POST /api/v1/sync (Riceve e salva dati inviati dal PC)
                    (uri.startsWith("/api/v1/sync") || uri == "/sync") && method == "POST" -> {
                        onLogActivity("Ricevuto payload SYNC POST dal PC")
                        val charBuffer = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val r = reader.read(charBuffer, read, contentLength - read)
                            if (r <= 0) break
                            read += r
                        }
                        val body = String(charBuffer, 0, read)
                        val jsonRoot = JSONObject(body)

                        var sentieriImportati = 0
                        var puntiImportati = 0

                        if (jsonRoot.has("sentieri")) {
                            val sentieriArray = jsonRoot.getJSONArray("sentieri")
                            for (i in 0 until sentieriArray.length()) {
                                val sObj = sentieriArray.getJSONObject(i)
                                val dto = SentieroDto.fromJson(sObj)

                                val idLoc = sObj.optInt("id", 0)
                                val entity = Sentieri(
                                    id = idLoc,
                                    nome = dto.nome,
                                    descrizione = dto.descrizione,
                                    lunghezza = dto.lunghezza,
                                    dislivello = dto.dislivello,
                                    discesa = dto.discesa,
                                    HrMed = dto.hrMed,
                                    HrMax = dto.hrMax,
                                    DataOra = dto.dataOra,
                                    TempMedia = dto.tempMedia,
                                    TempMax = dto.tempMax,
                                    TempMin = dto.tempMin,
                                    DataFine = dto.dataFine,
                                    TempoTot = dto.tempoTot,
                                    TempoInMov = dto.tempoInMov,
                                    MediaVel = dto.mediaVel,
                                    uuid = if (dto.uuid.isNotBlank()) dto.uuid else UUID.randomUUID().toString(),
                                    lastUpdate = if (dto.lastUpdate > 0) dto.lastUpdate else System.currentTimeMillis()
                                )
                                repository.sentieriDao.upsert(entity)
                                sentieriImportati++

                                if (sObj.has("punti")) {
                                    val puntiArray = sObj.getJSONArray("punti")
                                    val trackList = mutableListOf<Track>()
                                    for (j in 0 until puntiArray.length()) {
                                        val pObj = puntiArray.getJSONObject(j)
                                        val pDto = PuntoDto.fromJson(pObj)
                                        val t = Track(
                                            Trackid = idLoc,
                                            Latit = pDto.lat.toFloat(),
                                            Longit = pDto.lon.toFloat(),
                                            Ele = pDto.ele.toFloat(),
                                            Ora = pDto.time,
                                            trackUuid = dto.uuid,
                                            batteryPercent = pDto.batteryPercent,
                                            cadence = pDto.cadence,
                                            assistLevel = pDto.assistLevel,
                                            power = pDto.power,
                                            heartRate = pDto.heartRate
                                        )
                                        trackList.add(t)
                                    }
                                    if (trackList.isNotEmpty()) {
                                        repository.trackDao.upsertPoints(trackList)
                                        puntiImportati += trackList.size
                                    }
                                }
                            }
                        }

                        Log.d(TAG, "Sincronizzati dal PC: $sentieriImportati sentieri, $puntiImportati punti")
                        onLogActivity("Ricevuti dal PC: $sentieriImportati sentieri, $puntiImportati punti")
                        val responseJson = JSONObject().apply {
                            put("status", "ok")
                            put("importedSentieri", sentieriImportati)
                            put("importedPunti", puntiImportati)
                        }
                        sendResponse(output, 200, "OK", responseJson.toString())
                    }

                    else -> {
                        Log.w(TAG, "Endpoint non trovato: $uri")
                        sendResponse(output, 404, "Not Found", JSONObject().put("error", "Endpoint non trovato").toString())
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Errore durante gestione client HTTP ($clientIp)", e)
            onLogActivity("Errore connessione con $clientIp: ${e.localizedMessage}")
        } finally {
            try { socket.shutdownOutput() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun extractQueryParam(uri: String, paramName: String): String {
        val qIndex = uri.indexOf("?")
        if (qIndex < 0) return ""
        val query = uri.substring(qIndex + 1)
        return query.split("&")
            .firstOrNull { it.startsWith("$paramName=") }
            ?.substringAfter("=") ?: ""
    }

    private fun sendResponse(out: OutputStream, statusCode: Int, statusText: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) {
            out.write(bytes)
        }
        out.flush()
    }
}
