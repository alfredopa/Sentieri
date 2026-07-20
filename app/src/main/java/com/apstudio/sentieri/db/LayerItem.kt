package com.apstudio.sentieri.db

data class LayerItem(
    val nome: String,
    var abilitato: Boolean,
    var direzione: Boolean,
    var segui: Boolean,
    val distanza: Float,
    val ascesa: Int,
    val discesa: Int,
    var mostraPendenza: Boolean = true,
    val punti: List<org.osmdroid.util.GeoPoint> = emptyList(),
    var waypoints: List<net.federicomatera.agpxp.models.WayPoint> = emptyList()
)
