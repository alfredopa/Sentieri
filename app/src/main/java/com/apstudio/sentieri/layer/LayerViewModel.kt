package com.apstudio.sentieri.layer

import android.graphics.Color
import androidx.lifecycle.ViewModel
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.features.user.FeatureRow
import org.osmdroid.gpkg.overlay.features.PolygonOptions
import kotlin.random.Random
import kotlin.random.nextInt

class LayerViewModel : ViewModel() {
    val featureList = mutableListOf<FeatureTableInfo>()
    var geoPackageInstance: GeoPackage? = null
        set(value) {
            // If the new value is different from the current field
            if (field != value) {
                field = value // Update the field with the new value
                if (value != null) {
                    // New GeoPackage is set (and it's different or was null)
                    loadFeaturesFromGeoPackage()
                } else {
                    // GeoPackage is cleared (set to null)
                    featureList.clear()
                }
            }
        }
    // PARAMETRI per Polyline e Polygon
    val polygonOptions = PolygonOptions().apply {
        strokeWidth = 2f
        fillColor = Color.argb(100, 255, 0, 255)
        strokeColor = Color.argb(100, 0, 0, 0)
    }
    var labelConfig = mutableMapOf<String, List<Pair<String, Boolean>>>()
    val TAG = "LayerViewModel"
    var currentActiveTableName: String? = null // Era DATABASE_TABLE_NAME

    // Chiamato quando geoPackageInstance viene impostato per la prima volta
    fun loadFeaturesFromGeoPackage() {
        if (featureList.isEmpty()) { // Carica solo se la lista è vuota (per evitare ricariche non necessarie)
            geoPackageInstance?.featureTables?.map { tableName ->
                val contentsDao = geoPackageInstance?.contentsDao
                val contents = contentsDao?.queryForId(tableName)
                FeatureTableInfo(
                    tableName,
                    isVisible = false, // Lo stato iniziale è false, verrà ripristinato se necessario
                    descrTabella = contents?.identifier ?: "Nessuna descrizione",
                    colore = contents?.description ?: "#0000FF",
                    readData = false,
                    clusterIconResId = 0,
                    listOverlay = null

                )
            }?.let {
                featureList.addAll(it)
            }
        }
    }

    // Metodo per ripristinare lo stato (chiamato da onSaveInstanceState o simile se non usi ViewModel per tutto)
    // Se usi ViewModel correttamente, questo potrebbe non essere necessario se featureList stessa
    // è la source of truth e viene aggiornata direttamente.
    fun restoreVisibilityStates(visibilityMap: Map<String, Boolean>) {
        featureList.forEach { featureInfo ->
            featureInfo.isVisible = visibilityMap[featureInfo.name] ?: false
        }
    }


    fun creaLabel(featureRow: FeatureRow, tableName: String): String {
        val campiLabel = labelConfig[tableName]
        // val fieldsConfig = campiLabel // Non serve una variabile separata
        val labelBuilder = StringBuilder() // Usa StringBuilder
        campiLabel?.forEachIndexed { index, (fieldName, isVisible) ->
            if (isVisible) {
                // Usa append() per costruire la stringa
                labelBuilder.append(fieldName)
                    .append(": ")
                    .append(featureRow.values[index]?.toString() ?: "null") // Aggiunto controllo null per featureRow.values[index]
                    .append("\n")
            }
        }
        return labelBuilder.toString() // Converti StringBuilder in String alla fine
    }

    fun getRandomHexColor(): String {
        val red = Random.nextInt(256)
        val green = Random.nextInt(256)
        val blue = Random.nextInt(256)
        return String.format("#80%02x%02x%02x", red, green, blue)
    }


}

