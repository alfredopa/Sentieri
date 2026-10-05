package com.apstudio.sentieri.db

import androidx.annotation.DrawableRes
import com.apstudio.sentieri.R
import org.osmdroid.util.GeoPoint

data class TurnInstruction(
    val pointIndex: Int,
    val distanceAlongTrack: Double,
    val turnType: TurnType,
    val angle: Double = 0.0,
    val geoPoint: GeoPoint? = null
) {
    val description: String
        get() = when (turnType) {
            TurnType.LEFT, TurnType.TSHL -> "Gira a sinistra"
            TurnType.RIGHT, TurnType.TSHR -> "Gira a destra"
            TurnType.TSLL -> "Tieni la sinistra"
            TurnType.TSLR -> "Tieni la destra"
        }

    @get:DrawableRes
    val iconRes: Int
        get() = when (turnType) {
            TurnType.LEFT, TurnType.TSHL -> R.drawable.ic_turn_left
            TurnType.RIGHT, TurnType.TSHR -> R.drawable.ic_turn_right
            TurnType.TSLL -> R.drawable.ic_turn_slight_left
            TurnType.TSLR -> R.drawable.ic_turn_slight_right
        }
}

enum class TurnType {
    LEFT,          // TL
    RIGHT,         // TR
    TSHL,          // TSHL (Sharp Left)
    TSHR,          // TSHR (Sharp Right)
    TSLL,          // TSLL (Slight Left)
    TSLR;          // TSLR (Slight Right)

    companion object {
        fun fromBRouterSymbol(sym: String?): TurnType? {
            if (sym.isNullOrBlank()) return null
            return when (sym.trim().uppercase()) {
                "TL" -> LEFT
                "TR" -> RIGHT
                "TSHL" -> TSHL
                "TSHR" -> TSHR
                "TSLL" -> TSLL
                "TSLR" -> TSLR
                else -> null
            }
        }
    }
}
