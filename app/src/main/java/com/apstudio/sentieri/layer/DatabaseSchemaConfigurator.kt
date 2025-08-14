package com.apstudio.sentieri.layer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringWriter
import kotlin.collections.iterator

class DatabaseSchemaConfigurator(
    private val context: Context,
    private val dbName: String // Nome del database
) {

    companion object {
        private const val TAG = "DbSchemaConfigurator"
        private const val CONFIG_FILE_NAME = "db_schema_config.xml"
        private const val XML_TAG_ROOT = "databaseSchema"
        private const val XML_TAG_TABLE = "table"
        private const val XML_ATTR_TABLE_NAME = "name"
        private const val XML_TAG_FIELD = "field"
        private const val XML_ATTR_FIELD_NAME = "name"
        private const val XML_ATTR_FIELD_VISIBLE = "visible"
        private val ns: String? = null
    }

    /**
     * Legge lo schema del database e scrive un file di configurazione XML.
     * Ogni campo avrà un valore booleano iniziale (es. true di default).
     *
     * @param defaultVisibility Il valore booleano predefinito per la visibilità di ogni campo.
     * @return True se il file XML è stato scritto con successo, false altrimenti.
     */
    fun generateAndWriteConfigFile(defaultVisibility: Boolean = true): Boolean {
        val dbHelper = GenericDbHelper(context, dbName) // Un semplice helper per aprire il DB
        val db: SQLiteDatabase? = try {
            dbHelper.readableDatabase // Usiamo readableDatabase, ma potremmo aver bisogno di writable
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'aprire il database: $dbName", e)
            return false
        }

        if (db == null) {
            Log.e(TAG, "Impossibile aprire il database: $dbName")
            return false
        }

        val schemaData = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()

        try {
            // 1. Ottenere i nomi delle tabelle
            /*val tableCursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
                null
            )*/
            val tableCursor = db.rawQuery(
                "SELECT table_name FROM gpkg_contents WHERE data_type='features'",
                null
            )
            tableCursor.use { tc ->
                if (tc.moveToFirst()) {
                    do {
                        val tableName = tc.getString(0)
                        val fieldsList = mutableListOf<Pair<String, Boolean>>()

                        // 2. Ottenere i nomi dei campi per ogni tabella
                        val fieldCursor = db.rawQuery("PRAGMA table_info('$tableName')", null)
                        fieldCursor.use { fc ->
                            if (fc.moveToFirst()) {
                                do {
                                    val fieldName = fc.getString(fc.getColumnIndexOrThrow("name"))
                                    fieldsList.add(Pair(fieldName, defaultVisibility))
                                } while (fc.moveToNext())
                            }
                        }
                        if (fieldsList.isNotEmpty()) {
                            schemaData[tableName] = fieldsList
                        }
                    } while (tc.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la lettura dello schema del database", e)
            return false
        } finally {
            db.close()
        }

        if (schemaData.isEmpty()) {
            Log.w(TAG, "Nessuna tabella trovata o schema vuoto per il database: $dbName")
            return false // O true se un file vuoto è accettabile
        }

        // 3. Scrivere i dati in un file XML
        return writeSchemaToXml(schemaData)
    }

    private fun writeSchemaToXml(schemaData: Map<String, List<Pair<String, Boolean>>>): Boolean {
        val serializer: XmlSerializer = Xml.newSerializer()
        val writer = StringWriter() // Scriviamo prima su una stringa
        try {
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, XML_TAG_ROOT)

            for ((tableName, fields) in schemaData) {
                serializer.startTag(null, XML_TAG_TABLE)
                serializer.attribute(null, XML_ATTR_TABLE_NAME, tableName)
                for ((fieldName, isVisible) in fields) {
                    serializer.startTag(null, XML_TAG_FIELD)
                    serializer.attribute(null, XML_ATTR_FIELD_NAME, fieldName)
                    serializer.attribute(null, XML_ATTR_FIELD_VISIBLE, isVisible.toString())
                    serializer.endTag(null, XML_TAG_FIELD)
                }
                serializer.endTag(null, XML_TAG_TABLE)
            }

            serializer.endTag(null, XML_TAG_ROOT)
            serializer.endDocument()

            // Scrivi la stringa XML nel file
            val file = File(context.filesDir, CONFIG_FILE_NAME)
            FileOutputStream(file).use {
                it.write(writer.toString().toByteArray())
            }
            Log.i(TAG, "File di configurazione XML scritto in: ${file.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Errore durante la scrittura del file XML di configurazione", e)
        } catch (e: Exception) {
            Log.e(TAG, "Errore generico durante la serializzazione XML", e)
        }
        return false
    }

    /**
     * Legge il file di configurazione XML e carica i dati in una mappa.
     *
     * @return Una mappa dove la chiave è il nome della tabella e il valore è una lista di coppie
     *         (nome campo, visibilità booleana). Restituisce una mappa vuota in caso di errore
     *         o se il file non esiste.
     */
    fun loadConfigFromFile(): Map<String, List<Pair<String, Boolean>>> {
        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            Log.w(TAG, "File di configurazione non trovato: ${configFile.absolutePath}")
            return emptyMap()
        }

        val schemaConfig = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
        var currentTableName: String? = null
        var currentFieldsList: MutableList<Pair<String, Boolean>>? = null

        try {
            FileInputStream(configFile).use { fis ->
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(fis, null) // null per l'encoding, il parser lo rileverà

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                XML_TAG_TABLE -> {
                                    currentTableName = parser.getAttributeValue(ns, XML_ATTR_TABLE_NAME)
                                    if (currentTableName != null) {
                                        currentFieldsList = mutableListOf()
                                        schemaConfig[currentTableName] = currentFieldsList
                                    } else {
                                        Log.w(TAG, "Attributo '$XML_ATTR_TABLE_NAME' mancante per il tag '$XML_TAG_TABLE'")
                                    }
                                }
                                XML_TAG_FIELD -> {
                                    if (currentTableName != null && currentFieldsList != null) {
                                        val fieldName = parser.getAttributeValue(ns, XML_ATTR_FIELD_NAME)
                                        val fieldVisibleStr = parser.getAttributeValue(ns, XML_ATTR_FIELD_VISIBLE)
                                        if (fieldName != null && fieldVisibleStr != null) {
                                            val isVisible = fieldVisibleStr.toBooleanStrictOrNull() ?: false // Default a false se non parsabile
                                            currentFieldsList.add(Pair(fieldName, isVisible))
                                        } else {
                                            Log.w(TAG, "Attributi '$XML_ATTR_FIELD_NAME' o '$XML_ATTR_FIELD_VISIBLE' mancanti per il tag '$XML_TAG_FIELD' nella tabella '$currentTableName'")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == XML_TAG_TABLE) {
                                currentTableName = null
                                currentFieldsList = null
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Errore durante il parsing del file XML di configurazione", e)
            return emptyMap() // Restituisce mappa vuota in caso di errore di parsing
        } catch (e: IOException) {
            Log.e(TAG, "Errore di I/O durante la lettura del file XML di configurazione", e)
            return emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Errore generico durante la lettura della configurazione XML", e)
            return emptyMap()
        }

        Log.i(TAG, "Configurazione caricata con successo da ${configFile.absolutePath}")
        return schemaConfig
    }
}

