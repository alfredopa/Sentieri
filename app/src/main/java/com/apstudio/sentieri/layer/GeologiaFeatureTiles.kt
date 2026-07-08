package com.apstudio.sentieri.layer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.extension.nga.style.FeatureStyle
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.tiles.features.DefaultFeatureTiles
import mil.nga.sf.GeometryType
import androidx.core.graphics.toColorInt

// IMPORTANTE: Aggiungi GeoPackage al costruttore
class GeologiaFeatureTiles(context: Context, geoPackage: GeoPackage, featureDao: FeatureDao)
    : DefaultFeatureTiles(context, geoPackage, featureDao) {

    private val coloriGeologia = mapOf(
        "D232"	to "#7E4A8F".toColorInt(),
        "E232"	to "#2C9B54".toColorInt(),
        "E221"	to "#D1F03A".toColorInt(),
        "D212"	to "#8B1F4E".toColorInt(),
        "D224"	to "#3A7CA5".toColorInt(),
        "E211"	to "#E6A15C".toColorInt(),
        "C210"	to "#1C2D42".toColorInt(),
        "E122"	to "#B84D63".toColorInt(),
        "E233"	to "#F39C12".toColorInt(),
        "A222"	to "#4A90E2".toColorInt(),
        "A230"	to "#9B59B6".toColorInt(),
        "B222"	to "#1ABC9C".toColorInt(),
        "B223"	to "#D35400".toColorInt(),
        "D241"	to "#27AE60".toColorInt(),
        "E222"	to "#7F8C8D".toColorInt(),
        "B232"	to "#C0392B".toColorInt(),
        "A221"	to "#2C3E50".toColorInt(),
        "A223"	to "#F1C40F".toColorInt(),
        "B112"	to "#A1D2CE".toColorInt(),
        "B113"	to "#E84A5F".toColorInt(),
        "B211"	to "#34495E".toColorInt(),
        "B241"	to "#16A085".toColorInt(),
        "D221"	to "#2ECC71".toColorInt(),
        "B212"	to "#E74C3C".toColorInt(),
        "B225"	to "#8E44AD".toColorInt(),
        "E142"	to "#3498DB".toColorInt(),
        "C221"	to "#F39A27".toColorInt(),
        "C222"	to "#A52A2A".toColorInt(),
        "D100"	to "#5F9EA0".toColorInt(),
        "D223"	to "#7FFF00".toColorInt(),
        "E110"	to "#D2691E".toColorInt(),
        "E121"	to "#FF7F50".toColorInt(),
        "A220"	to "#6495ED".toColorInt(),
        "E223"	to "#DC143C".toColorInt(),
        "E224"	to "#00FFFF".toColorInt(),
        "E231"	to "#B8860B".toColorInt(),
        "B253"	to "#A9A9A9".toColorInt(),
        "B111"	to "#A0522D".toColorInt(),
        "A225"	to "#2F4F4F".toColorInt(),
        "A224"	to "#FF8C00".toColorInt(),
        "B120"	to "#9932CC".toColorInt(),
        "B114"	to "#8B0000".toColorInt(),
        "B250"	to "#E9967A".toColorInt(),
        "B254"	to "#8FBC8F".toColorInt(),
        "B310"	to "#483D8B".toColorInt(),
        "B256"	to "#2F4F4F".toColorInt(),
        "B221"	to "#00CED1".toColorInt(),
        "B224"	to "#9400D3".toColorInt(),
        "B231"	to "#FF1493".toColorInt(),
        "B245"	to "#00BFFF".toColorInt(),
        "B244"	to "#696969".toColorInt(),
        "B242"	to "#1E90FF".toColorInt(),
        "B243"	to "#B22222".toColorInt(),
        "B252"	to "#FFFAF0".toColorInt(),
        "B251"	to "#228B22".toColorInt(),
        "B255"	to "#FF00FF".toColorInt(),
        "B257"	to "#DAA520".toColorInt(),
        "B320"	to "#808080".toColorInt(),
        "C110"	to "#008000".toColorInt(),
        "C120"	to "#ADFF2F".toColorInt(),
        "C310"	to "#F0FFF0".toColorInt(),
        "C320"	to "#FF69B4".toColorInt(),
        "D213"	to "#CD5C5C".toColorInt(),
        "D211"	to "#4B0082".toColorInt(),
        "E000"	to "#FFFFF0".toColorInt(),
        "E132"	to "#F0E68C".toColorInt(),
        "E133"	to "#E6E6FA".toColorInt(),
        "E141"	to "#FFF0F5".toColorInt(),
        "E131"	to "#7CFC00".toColorInt(),
        "D231"	to "#FFFACD".toColorInt(),
        "D222"	to "#ADD8E6".toColorInt(),
        "E225"	to "#F08080".toColorInt(),
        "E234"	to "#E0FFFF".toColorInt()
    )

    // Usiamo una variabile semplice. Poiché osmdroid renderizza una tile alla volta,
    // questo è sicuro e molto più veloce.
    private var rowInCorso: FeatureRow? = null

    override fun getFeatureStyle(featureRow: FeatureRow?): FeatureStyle? {
        rowInCorso = featureRow
        return super.getFeatureStyle(featureRow)
    }

    override fun getFeatureStyle(featureRow: FeatureRow?, geometryType: GeometryType?): FeatureStyle? {
        rowInCorso = featureRow
        return super.getFeatureStyle(featureRow, geometryType)
    }

    override fun getPolygonFillPaint(featureStyle: FeatureStyle?): Paint {
        // Fondamentale: creiamo un Paint partendo da quello di default della libreria
        val paint = Paint(polygonFillPaint)

        val row = rowInCorso
        if (row != null) {
            val codice = row.getValueString("UNITAGERAR") ?: ""
            paint.color = coloriGeologia[codice] ?: Color.GRAY
            paint.alpha = 140
        }

        return paint
    }

    override fun getPolygonPaint(featureStyle: FeatureStyle?): Paint {
        val paint = Paint(polygonPaint)
        paint.color = Color.BLACK
        paint.strokeWidth = 0.5f
        paint.alpha = 60
        return paint
    }
}