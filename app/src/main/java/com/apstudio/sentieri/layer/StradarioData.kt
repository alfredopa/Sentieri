package com.apstudio.sentieri.layer

import org.osmdroid.util.GeoPoint

data class StradaData(
    val nomeComune: String,
    val nomeVia: String,
    val highway: String?,
    val segments: List<List<GeoPoint>>
)
