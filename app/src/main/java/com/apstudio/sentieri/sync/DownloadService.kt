package com.apstudio.sentieri.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.BuildConfig
import com.apstudio.sentieri.MainActivity
import com.apstudio.sentieri.MapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null

    companion object {
        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"
        const val NOTIFICATION_ID = 5678
        const val CHANNEL_ID = "DOWNLOAD_SERVICE_CHANNEL"
        private const val TAG = "DownloadService"

        const val ACTION_PROGRESS_UPDATE = "com.apstudio.sentieri.PROGRESS_UPDATE"
        const val ACTION_DOWNLOAD_STARTED = "com.apstudio.sentieri.DOWNLOAD_STARTED"
        const val ACTION_DOWNLOAD_COMPLETE = "com.apstudio.sentieri.DOWNLOAD_COMPLETE"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"

        const val PROGRESS_DECOMPRESSING = -2
    }

    object ServerConfig {
        private fun raw(prefs: SharedPreferences): String {
            val s = prefs.getString("nas_server_url", "https://sentieriap.duckdns.org")?.trim().orEmpty().trimEnd('/')
            require(s.isNotEmpty()) { "Server non configurato nelle impostazioni" }
            return if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        }

        /** Radice: https://sentieriap.duckdns.org  (per sync-*.php) */
        fun baseUrl(prefs: SharedPreferences) = raw(prefs).removeSuffix("/files")

        /** https://sentieriap.duckdns.org/files  (per download e upload) */
        fun filesUrl(prefs: SharedPreferences) = baseUrl(prefs) + "/files"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        val initialNotification = createNotification("Preparazione download...", 0)
        startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        if (intent?.action == ACTION_START_DOWNLOAD) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            if (filePath != null) {
                startDownload(filePath)  // NOME DEL FILE da scaricare
                Log.d("DownloadonStartCommand", "filePath $filePath EXTRA $EXTRA_FILE_PATH")
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(percorsoFileRemoto: String) {
        downloadJob = serviceScope.launch {
            var downloadSuccess = false
            var errorMessage = ""

            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@DownloadService)
                //val serverUrl = prefs.getString("nas_server_url", "https://sentieriapduckdns.org:443")?.trimEnd('/') ?: ""
                val serverUrl = ServerConfig.filesUrl(prefs)
                val token = BuildConfig.NAS_AUTH_TOKEN

                if (serverUrl.isEmpty() || token.isEmpty()) {
                    throw Exception("Configurazione NAS incompleta nelle impostazioni (URL o Token mancante)")
                }

                val nomeFile = percorsoFileRemoto.substringAfterLast("/")
                val deveScompattare = nomeFile.contains(".zip", ignoreCase = true)
                val localFile: File

                if (deveScompattare) {
                    val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
                    localFile = File(downloadDir, nomeFile)
                } else {
                    val appMediaDir = externalMediaDirs.getOrNull(0) ?: getExternalFilesDir(null) ?: cacheDir
                    val mappeDir = File(appMediaDir, "Mappe")
                    if (!mappeDir.exists()) mappeDir.mkdirs()
                    localFile = File(mappeDir, nomeFile)
                }

                // Supporto ripresa download (Range) se il file esiste già parzialmente
                val existingLength = if (localFile.exists()) localFile.length() else 0L

                val encodedPath = URLEncoder.encode(percorsoFileRemoto, "UTF-8")
                val url = URL("$serverUrl/api/file?path=Cloud/$encodedPath")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Authorization", "Bearer $token")
                    if (existingLength > 0) {
                        setRequestProperty("Range", "bytes=$existingLength-")
                    }
                }

                val responseCode = connection.responseCode
                val appendMode: Boolean
                val totalExpectedSize: Long

                when (responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        // Risposta 206 Partial Content: riprende da existingLength
                        appendMode = true
                        val contentLength = connection.contentLengthLong
                        totalExpectedSize = if (contentLength > 0) existingLength + contentLength else -1L
                        Log.d(TAG, "Download parziale ripreso da byte $existingLength su $totalExpectedSize")
                    }
                    HttpURLConnection.HTTP_OK -> {
                        // Risposta 200 OK: riscarica da zero
                        appendMode = false
                        totalExpectedSize = connection.contentLengthLong
                    }
                    416 -> {
                        // Range Not Satisfiable: file già completato o oltre fine file
                        if (localFile.exists() && localFile.length() > 0) {
                            totalExpectedSize = localFile.length()
                            appendMode = true
                            downloadSuccess = true
                        } else {
                            throw Exception("Errore Range non valido (416)")
                        }
                    }
                    401 -> throw Exception("Accesso non autorizzato (401): verifica il token nelle impostazioni")
                    429 -> throw Exception("Troppi tentativi falliti (429): IP temporaneamente bloccato dal NAS")
                    else -> throw Exception("Errore server HTTP $responseCode: ${connection.responseMessage}")
                }

                if (!downloadSuccess) {
                    val outputStream = FileOutputStream(localFile, appendMode)
                    sendProgressBroadcast(0, nomeFile)

                    val inputStream = connection.inputStream
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var currentDownloadedBytes = if (appendMode) existingLength else 0L
                    var lastReportedProgress = -1
                    var lastUpdateTime = 0L

                    outputStream.use { output ->
                        inputStream.use { input ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!serviceScope.isActive) break
                                output.write(buffer, 0, bytesRead)
                                currentDownloadedBytes += bytesRead

                                val progress = if (totalExpectedSize > 0) {
                                    ((currentDownloadedBytes * 100) / totalExpectedSize).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }

                                val currentTime = System.currentTimeMillis()
                                if (progress != lastReportedProgress && (currentTime - lastUpdateTime > 400 || progress == 100)) {
                                    updateNotification(nomeFile, progress)
                                    sendProgressBroadcast(progress, nomeFile)
                                    lastReportedProgress = progress
                                    lastUpdateTime = currentTime
                                }
                            }
                        }
                    }

                    downloadSuccess = (totalExpectedSize <= 0) || (currentDownloadedBytes == totalExpectedSize)
                }

                if (downloadSuccess) {
                    if (deveScompattare) {
                        if (localFile.exists()) {
                            updateNotification(nomeFile, PROGRESS_DECOMPRESSING)
                            sendProgressBroadcast(PROGRESS_DECOMPRESSING, nomeFile)
                            val scompattato = MapUtils.decomprimiZipInCartellaMappe(this@DownloadService, nomeFile)
                            if (scompattato) {
                                errorMessage = "Download e scompattamento completati: $nomeFile"
                                try { localFile.delete() } catch (_: Exception) {}
                            } else {
                                errorMessage = "Errore durante lo scompattamento"
                                downloadSuccess = false
                            }
                        } else {
                            downloadSuccess = false
                        }
                    }
                }

                if (errorMessage.isEmpty()) {
                    errorMessage = if (downloadSuccess) {
                        "Download completato: $nomeFile"
                    } else {
                        "Errore durante il download del file"
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                errorMessage = "Errore: ${e.message}"
            } finally {
                sendResultBroadcast(errorMessage)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        Log.d("Download", "content=$content")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Mappa")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val content = when (progress) {
            PROGRESS_DECOMPRESSING -> "Decompressione $fileName..."
            in 0..100 -> "Scaricando $fileName: $progress%"
            else -> "Scaricando $fileName..."
        }
        Log.d("DownloadupdateNotification", "content=$content filname $fileName" )
        val notification = createNotification(content, if (progress in 0..100) progress else 0)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendProgressBroadcast(progress: Int, fileName: String? = null) {
        val intent = Intent(if (progress == 0) ACTION_DOWNLOAD_STARTED else ACTION_PROGRESS_UPDATE).apply {
            putExtra(EXTRA_PROGRESS, progress)
            fileName?.let { putExtra(EXTRA_FILE_PATH, it) }
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendResultBroadcast(message: String) {
        val intent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Download Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }
}