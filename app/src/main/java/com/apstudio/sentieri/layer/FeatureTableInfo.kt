package com.apstudio.sentieri.layer

import org.osmdroid.views.overlay.Overlay

data class FeatureTableInfo(
    val name: String,
    var isVisible: Boolean = false,
    val descrTabella: String,
    // val numRecord: Int,
    val colore: String,
    var readData: Boolean = false,
    val clusterIconResId: Int = 0,// ID risorsa per l'icona del cluster (opzionale)
    var listOverlay: MutableList<Overlay>? = null
)