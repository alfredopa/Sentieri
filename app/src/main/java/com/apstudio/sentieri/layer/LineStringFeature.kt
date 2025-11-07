package com.apstudio.sentieri.layer

data class LineStringFeature(
    val lineString: mil.nga.sf.LineString,
    val title: String,
    val description: String // O qualsiasi altra info ti serva
)