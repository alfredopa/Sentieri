package com.apstudio.sentieri

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class GpxImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingIntent = intent
        val uri: Uri? = incomingIntent?.data

        if (uri != null && incomingIntent?.action == Intent.ACTION_VIEW) {
            // Concede esplicitamente il permesso di lettura dell'URI al package dell'applicazione
            try {
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w("GpxImportActivity", "Impossibile concedere il permesso URI: ${e.message}")
            }

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, incomingIntent.type)
                // Imposta ClipData per garantire il passaggio del permesso di lettura URI a MainActivity su Android 10+
                clipData = incomingIntent.clipData ?: ClipData.newRawUri("gpx_file", uri)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(mainIntent)
        }
        finish()
    }
}
