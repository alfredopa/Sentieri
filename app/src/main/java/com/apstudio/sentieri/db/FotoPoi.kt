package com.apstudio.sentieri.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FotoPoi")
data class FotoPoi(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "Trackid")
    var trackid: Int,
    @ColumnInfo(name = "UriPath")
    var uriPath: String,
    @ColumnInfo(name = "NomeFoto")
    var nomeFoto: String
)