package com.apstudio.sentieri.db

import java.util.UUID

data class TopoMarkerData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double
)
