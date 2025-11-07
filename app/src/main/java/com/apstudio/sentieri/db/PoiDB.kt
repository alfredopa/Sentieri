package com.apstudio.sentieri.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "PoiDB")
data class PoiDB(
    @PrimaryKey(autoGenerate = true)
    val Id: Int = 0,
    @ColumnInfo(name = "Trackid")
    var Trackid: Int,
    @ColumnInfo(name = "Lat")
    var Latit: Double,
    @ColumnInfo(name = "Lon")
    var Longit: Double,
    @ColumnInfo(name = "Ele")
    var Ele: Double,
    @ColumnInfo(name = "NomePOI")
    var NomePOI: String,
    @ColumnInfo(name = "DescrPOI")
    var DescrPOI: String,
    @ColumnInfo(name = "UriPath")
    var UriPath: String,
    @ColumnInfo(name = "Time")
    var Time: String
)
