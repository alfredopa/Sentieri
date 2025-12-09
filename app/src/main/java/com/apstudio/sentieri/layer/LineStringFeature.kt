package com.apstudio.sentieri.layer

import mil.nga.sf.LineString

data class LineStringFeature(
    val lineString: LineString,
    val title: String,
    val description: String,
    val website: String? // Campo aggiunto per l'URL del sito web
)
