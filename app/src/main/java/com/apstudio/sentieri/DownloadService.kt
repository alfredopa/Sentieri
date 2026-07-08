package com.apstudio.sentieri

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        const val PROGRESS_DECOMPRESSING = -2
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
            val ftpClient = FTPClient()
            var downloadSuccess = false
            var errorMessage = ""

            try {
                ftpClient.defaultTimeout = 10000
                ftpClient.connect(BuildConfig.FTP_SERVER, BuildConfig.FTP_PORT)
                ftpClient.login(BuildConfig.FTP_USER, BuildConfig.FTP_PASS)
                ftpClient.soTimeout = 10000
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

                var fileSize = -1L
                try {
                    val file = ftpClient.mlistFile(percorsoFileRemoto)
                    if (file != null) {
                        fileSize = file.size
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "MLST non supportato, provo con SIZE")
                    val reply = ftpClient.sendCommand("SIZE", percorsoFileRemoto)
                    if (FTPReply.isPositiveCompletion(reply)) {
                        val replyString = ftpClient.replyStrings[0]
                        fileSize = replyString.split(" ").getOrNull(1)?.toLongOrNull() ?: -1L
                    }
                }
                
                val nomeFile = percorsoFileRemoto.substringAfterLast("/")
                val deveScompattare = nomeFile.contains(".zip", ignoreCase = true)
                val localFile: File

                if (deveScompattare) {
                    val cartellaDownloadPubblica = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    localFile = File(cartellaDownloadPubblica, nomeFile)
                } else {
                    val appMediaDir = externalMediaDirs.getOrNull(0)
                    val mappeDir = File(appMediaDir, "Mappe")
                    if (!mappeDir.exists()) mappeDir.mkdirs()
                    localFile = File(mappeDir, nomeFile)
                }
                
                val outputStream = FileOutputStream(localFile)
                sendProgressBroadcast(0, nomeFile) 

                val inputStream = ftpClient.retrieveFileStream(percorsoFileRemoto)
                if (inputStream != null) {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastReportedProgress = -1
                    var lastUpdateTime = 0L

                    outputStream.use { output ->
                        inputStream.use { input ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!serviceScope.isActive) break
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                
                                val progress = if (fileSize > 0) {
                                    ((totalBytesRead * 100) / fileSize).toInt()
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
                    try { inputStream.close() } catch (_: Exception) {}
                    downloadSuccess = ftpClient.completePendingCommand()
                }

                if (downloadSuccess && fileSize > 0) {
                    if (deveScompattare) {
                        if (localFile.exists() && localFile.length() == fileSize) {
                            // File scaricato correttamente, ora decomprimiamo
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
