package com.apstudio.sentieri.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "FotoPoi")
data class FotoPoi(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "Trackid")
    var trackid: Int,
    @ColumnInfo(name = "UriPath")
    var uriPath: String,
    @ColumnInfo(name = "NomeFoto")
    var nomeFoto: String,
    @ColumnInfo(name = "uuid")
    var uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "trackUuid")
    var trackUuid: String = "",
    @ColumnInfo(name = "lastUpdate")
    var lastUpdate: Long = System.currentTimeMillis()
)