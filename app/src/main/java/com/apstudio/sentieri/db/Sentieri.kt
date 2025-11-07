package com.apstudio.sentieri.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.util.Locale

@Entity(tableName = "Sentieri")
data class Sentieri(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "Nome")
    var nome: String,
    @ColumnInfo(name = "Descrizione")
    var descrizione: String,
    @ColumnInfo(name = "Lunghezza")
    var lunghezza: Double,
    @ColumnInfo(name = "Dislivello")
    var dislivello: Int,
    @ColumnInfo(name = "Discesa")
    var discesa: Int,
    @ColumnInfo(name = "HrMed")
    var HrMed: Int,
    @ColumnInfo(name = "HrMax")
    var HrMax: Int,
    @ColumnInfo(name = "DataOra")
    var DataOra: String,
    @ColumnInfo(name = "TempMedia")
    var TempMedia: Double,
    @ColumnInfo(name = "TempMax")
    var TempMax: Double,
    @ColumnInfo(name = "TempMin")
    var TempMin: Double,
    @ColumnInfo(name = "DataFine")
    var DataFine: String,
    @ColumnInfo(name = "TempoTot")
    var TempoTot: Double,
    @ColumnInfo(name = "TempoInMov")
    var TempoInMov: Double,
    @ColumnInfo(name = "MediaVel")
    var MediaVel: Double
    )

fun Sentieri.prnLunghezza(): String =
    NumberFormat.getInstance(Locale.ITALIAN).format(lunghezza)

fun Sentieri.prnDislivello(): String =
    NumberFormat.getInstance(Locale.ITALIAN).format(dislivello)

fun Sentieri.prnDiscesa(): String =
    NumberFormat.getInstance(Locale.ITALIAN).format(discesa)



