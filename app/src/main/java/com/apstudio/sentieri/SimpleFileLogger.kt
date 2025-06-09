package com.apstudio.sentieri

import android.app.Application
import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SimpleFileLogger {

    private lateinit var appContext: Context
    private const val DIRECTORY_NAME = "app_logs"
    private val fileNameDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized // Sincronizza per evitare problemi di accesso concorrente al file
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        if (!::appContext.isInitialized) {
            android.util.Log.e("SimpleFileLogger", "Logger non inizializzato!")
            return
        }

        try {
            val logDirectory = File(appContext.getExternalFilesDir(null), DIRECTORY_NAME)
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }

            val logFile = File(logDirectory, "log_${fileNameDateFormat.format(Date())}.txt")
            val fileWriter = FileWriter(logFile, true) // true per appendere

            fileWriter.append("${logDateFormat.format(Date())} D/${tag}: ${message}\n")
            if (throwable != null) {
                fileWriter.append(android.util.Log.getStackTraceString(throwable))
                fileWriter.append("\n")
            }
            fileWriter.flush()
            fileWriter.close()
        } catch (e: Exception) {
            android.util.Log.e("SimpleFileLogger", "Errore durante la scrittura del log su file", e)
        }
    }
}

// Per usarlo:
// SimpleFileLogger.log("MappaFragment", "onCreate chiamato")