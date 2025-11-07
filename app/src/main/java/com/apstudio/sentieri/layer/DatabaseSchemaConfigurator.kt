package com.apstudio.sentieri.layer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.Xml
import com.apstudio.sentieri.db.FieldSchemaInfo // Assicurati che FieldSchemaInfo sia una data class ad es. data class FieldSchemaInfo(val name: String, val description: String, val visible: Boolean)
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringWriter
// import kotlin.collections.iterator // Questo import potrebbe non essere necessario se non usi esplicitamente iterator()

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
        private const val XML_DESCR_FIELD_NAME = "description"
        private const val XML_ATTR_FIELD_VISIBLE = "visible"
        private val ns: String? = null
    }

    fun generateAndWriteConfigFile(defaultVisibility: Boolean = true): Boolean {
        val dbHelper = GenericDbHelper(context, dbName)
        val db: SQLiteDatabase? = try {
            dbHelper.readableDatabase
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'aprire il database: $dbName", e)
            return false
        }

        if (db == null) {
            Log.e(TAG, "Impossibile aprire il database: $dbName")
            return false
        }

        // MODIFICATO: Usa FieldSchemaInfo qui
        val schemaData = mutableMapOf<String, MutableList<FieldSchemaInfo>>()

        try {
            val tableCursor = db.rawQuery(
                "SELECT table_name FROM gpkg_contents WHERE data_type='features'",
                null
            )
            tableCursor.use { tc ->
                if (tc.moveToFirst()) {
                    do {
                        val tableName = tc.getString(0)
                        val fieldsList : MutableList<FieldSchemaInfo> = mutableListOf() // Questo è corretto

                        val fieldCursor = db.rawQuery("PRAGMA table_info('$tableName')", null)
                        fieldCursor.use { fc ->
                            if (fc.moveToFirst()) {
                                do {
                                    val fieldName = fc.getString(fc.getColumnIndexOrThrow("name"))
                                    // Assumiamo che FieldSchemaInfo abbia un costruttore (String, String, Boolean)
                                    fieldsList.add(FieldSchemaInfo(fieldName, fieldName.lowercase(), defaultVisibility))
                                } while (fc.moveToNext())
                            }
                        }
                        if (fieldsList.isNotEmpty()) {
                            schemaData[tableName] = fieldsList // Ora i tipi corrispondono
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
            return false
        }

        return writeSchemaToXml(schemaData)
    }

    // MODIFICATO: Il parametro ora usa List<FieldSchemaInfo>
    private fun writeSchemaToXml(schemaData: Map<String, List<FieldSchemaInfo>>): Boolean {
        val serializer: XmlSerializer = Xml.newSerializer()
        val writer = StringWriter()
        try {
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, XML_TAG_ROOT)

            for ((tableName, fields) in schemaData) {
                serializer.startTag(null, XML_TAG_TABLE)
                serializer.attribute(null, XML_ATTR_TABLE_NAME, tableName)
                // MODIFICATO: Itera su FieldSchemaInfo
                for (fieldInfo in fields) { // Accedi alle proprietà di fieldInfo
                    serializer.startTag(null, XML_TAG_FIELD)
                    serializer.attribute(null, XML_ATTR_FIELD_NAME, fieldInfo.name)
                    serializer.attribute(null, XML_DESCR_FIELD_NAME, fieldInfo.description)
                    serializer.attribute(null, XML_ATTR_FIELD_VISIBLE, fieldInfo.isVisible.toString())
                    serializer.endTag(null, XML_TAG_FIELD)
                }
                serializer.endTag(null, XML_TAG_TABLE)
            }

            serializer.endTag(null, XML_TAG_ROOT)
            serializer.endDocument()

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

    // MODIFICATO: Il tipo di ritorno ora usa List<FieldSchemaInfo>
    fun loadConfigFromFile(): Map<String, List<FieldSchemaInfo>> {
        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            Log.w(TAG, "File di configurazione non trovato: ${configFile.absolutePath}")
            return emptyMap()
        }

        // MODIFICATO: Usa FieldSchemaInfo qui
        val schemaConfig = mutableMapOf<String, MutableList<FieldSchemaInfo>>()
        var currentTableName: String? = null
        // currentFieldsList è già correttamente MutableList<FieldSchemaInfo>?
        var currentFieldsList: MutableList<FieldSchemaInfo>? = null

        try {
            FileInputStream(configFile).use { fis ->
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(fis, null)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                XML_TAG_TABLE -> {
                                    currentTableName = parser.getAttributeValue(ns, XML_ATTR_TABLE_NAME)
                                    if (currentTableName != null) {
                                        currentFieldsList = mutableListOf() // Crea una lista di FieldSchemaInfo
                                        schemaConfig[currentTableName!!] = currentFieldsList!! // Ora i tipi corrispondono
                                    } else {
                                        Log.w(TAG, "Attributo '$XML_ATTR_TABLE_NAME' mancante per il tag '$XML_TAG_TABLE'")
                                    }
                                }
                                XML_TAG_FIELD -> {
                                    if (currentTableName != null && currentFieldsList != null) {
                                        val fieldName = parser.getAttributeValue(ns, XML_ATTR_FIELD_NAME)
                                        // Leggi la descrizione, usa il nome del campo in minuscolo come fallback se non presente
                                        var fieldDescr = parser.getAttributeValue(ns, XML_DESCR_FIELD_NAME)
                                        val fieldVisibleStr = parser.getAttributeValue(ns, XML_ATTR_FIELD_VISIBLE)

                                        if (fieldName != null && fieldVisibleStr != null) {
                                            if (fieldDescr == null) { // Fallback per la descrizione
                                                fieldDescr = fieldName.lowercase()
                                                Log.w(TAG, "Attributo '$XML_DESCR_FIELD_NAME' mancante per il tag '$XML_TAG_FIELD' nella tabella '$currentTableName'. Uso '$fieldDescr' come fallback.")
                                            }
                                            val isVisible = fieldVisibleStr.toBooleanStrictOrNull() ?: false
                                            // MODIFICATO: Crea e aggiungi FieldSchemaInfo
                                            currentFieldsList.add(FieldSchemaInfo(fieldName, fieldDescr, isVisible))
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
                                currentFieldsList = null // Resetta la lista corrente
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Errore durante il parsing del file XML di configurazione", e)
            return emptyMap()
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
