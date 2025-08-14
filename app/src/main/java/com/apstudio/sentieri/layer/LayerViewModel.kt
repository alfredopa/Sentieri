package com.apstudio.sentieri.layer

import androidx.lifecycle.ViewModel
import mil.nga.geopackage.GeoPackage

class LayerViewModel : ViewModel() {
    val featureList = mutableListOf<FeatureTableInfo>()
    var geoPackageInstance: GeoPackage? = null
        set(value) {
            if (field == null && value != null) { // Carica solo la prima volta o se il geopackage cambia
                field = value
                loadFeaturesFromGeoPackage()
            } else if (value == null) {
                field = null
                featureList.clear()
            }
        }

    var currentActiveTableName: String? = null // Era DATABASE_TABLE_NAME

    // Chiamato quando geoPackageInstance viene impostato per la prima volta
    private fun loadFeaturesFromGeoPackage() {
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
}
