package com.apstudio.sentieri.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "Track")
data class Track(
    @PrimaryKey(autoGenerate = true)
    val Id: Int = 0,
    @ColumnInfo(name = "Trackid")
    var Trackid: Int,
    @ColumnInfo(name = "Lat")
    var Latit: Float,
    @ColumnInfo(name = "Lon")
    var Longit: Float,
    @ColumnInfo(name = "Ele")
    var Ele: Float,
    @ColumnInfo(name = "Time")
    var Ora: String,
    @ColumnInfo(name = "trackUuid")
    var trackUuid: String = "",
    @ColumnInfo(name = "batteryPercent")
    var batteryPercent: Int? = null,
    @ColumnInfo(name = "cadence")
    var cadence: Int? = null,
    @ColumnInfo(name = "assistLevel")
    var assistLevel: Int? = null,
    @ColumnInfo(name = "power")
    var power: Int? = null,
    @ColumnInfo(name = "heartRate")
    var heartRate: Int? = null
)