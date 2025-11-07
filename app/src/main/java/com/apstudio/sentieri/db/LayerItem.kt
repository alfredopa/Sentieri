package com.apstudio.sentieri.db

data class LayerItem(
    val nome: String,
    var abilitato: Boolean,
    var direzione: Boolean,
    var segui: Boolean,
    val distanza: Float,
    val ascesa: Int,
    val discesa: Int,
)
