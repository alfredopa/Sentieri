package com.apstudio.sentieri

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        // Obbligatorio: Chiama startForeground immediatamente per evitare crash su Android 12+
        val initialNotification = createNotification("Preparazione download...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        if (intent?.action == ACTION_START_DOWNLOAD) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            if (filePath != null) {
                startDownload(filePath)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(percorsoFileRemoto: String) {
        downloadJob = serviceScope.launch {
            val ftpClient = FTPClient()
            var downloadSuccess = false
            val fileScaricato: File?
            var errorMessage = ""

            try {
                ftpClient.defaultTimeout = 10000
                ftpClient.connect(BuildConfig.FTP_SERVER, BuildConfig.FTP_PORT)
                ftpClient.login(BuildConfig.FTP_USER, BuildConfig.FTP_PASS)
                ftpClient.setSoTimeout(10000)
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

                var fileSize = -1L
                val reply = ftpClient.sendCommand("SIZE", percorsoFileRemoto)
                if (FTPReply.isPositiveCompletion(reply)) {
                    val parts = ftpClient.replyStrings[0].split(" ")
                    if (parts.size >= 2) fileSize = parts[1].toLong()
                }
                
                val nomeFile = percorsoFileRemoto.substringAfterLast("/")
                
                // Determina la destinazione
                val nomeFileDaSalvare = percorsoFileRemoto.substringAfterLast("/")
                val deveScompattare = nomeFileDaSalvare.contains(".zip", ignoreCase = true)
                
                val outputStream: FileOutputStream
                val localFile: File

                if (deveScompattare) {
                    // Se è uno ZIP, usiamo la cartella pubblica Downloads (come previsto in MapUtils)
                    val cartellaDownloadPubblica = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    localFile = File(cartellaDownloadPubblica, nomeFileDaSalvare)
                } else {
                    // Altrimenti cartella privata dell'app (Android/media/com.apstudio.sentieri)
                    val appMediaDir = externalMediaDirs.getOrNull(0)
                    val mappeDir = File(appMediaDir, "Mappe")
                    if (!mappeDir.exists()) mappeDir.mkdirs()
                    localFile = File(mappeDir, nomeFile)
                }
                
                outputStream = FileOutputStream(localFile)
                sendBroadcast(Intent(ACTION_DOWNLOAD_STARTED))

                val inputStream = ftpClient.retrieveFileStream(percorsoFileRemoto)
                if (inputStream != null) {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    outputStream.use { output ->
                        inputStream.use { input ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!serviceScope.isActive) break
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (fileSize > 0) {
                                    val progress = ((totalBytesRead * 100) / fileSize).toInt()
                                    Log.d("FTP_Progress", "Progresso manuale: $progress%")
                                    updateNotification(nomeFile, progress)
                                    sendProgressBroadcast(progress)
                                }
                            }
                        }
                    }
                    downloadSuccess = ftpClient.completePendingCommand()
                }
                // 6. Chiudi l'input stream dopo aver finito di leggere
                inputStream.close()

                // --- Controllo Integrità e Post-Processamento ---
                if (downloadSuccess && fileSize > 0) {
                    // Controllo integrità solo se il file è destinato alla cartella pubblica (dove è più facile verificare la dimensione)
                    if (deveScompattare) {
                        @Suppress("DEPRECATION")
                        val cartellaDownloadPubblica = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        fileScaricato = File(cartellaDownloadPubblica, nomeFileDaSalvare)

                        if (fileScaricato.exists()) {
                            val dimensioneReale = fileScaricato.length()
                            Log.d(
                                "FTP",
                                "Controllo integrità: Dimensione attesa=$fileSize, Dimensione reale=$dimensioneReale"
                            )
                            if (dimensioneReale != fileSize) {
                                Log.e(
                                    "FTP",
                                    "Il file è incompleto! Il download verrà considerato fallito."
                                )
                                downloadSuccess = false // <-- CRUCIALE: Marca il download come fallito
                            }
                        } else {
                            Log.w("FTP", "Impossibile trovare il file scaricato per il controllo di integrità.")
                            downloadSuccess = false
                        }
                    } else {
                        // Per i file non-zip salvati nella cartella app, consideriamo il successo alla chiusura dello stream.
                        downloadSuccess = true
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                errorMessage = "Errore: ${e.message}"
            } finally {
                if (ftpClient.isConnected) {
                    try { ftpClient.logout(); ftpClient.disconnect() } catch (_: Exception) {}
                }
                sendResultBroadcast(errorMessage)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotification(content: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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
        val notification = createNotification("Scaricando $fileName: $progress%", progress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendProgressBroadcast(progress: Int) {
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra(EXTRA_PROGRESS, progress)
        }
        sendBroadcast(intent)
    }

    private fun sendResultBroadcast(message: String) {
        val intent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Download Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }
}
