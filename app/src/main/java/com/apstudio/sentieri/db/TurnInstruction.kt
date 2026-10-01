package com.apstudio.sentieri.db

import androidx.annotation.DrawableRes
import com.apstudio.sentieri.R

data class TurnInstruction(
    val pointIndex: Int,
    val distanceAlongTrack: Double,
    val turnType: TurnType,
    val angle: Double
) {
    val description: String
        get() = when (turnType) {
            TurnType.LEFT -> "Gira a sinistra"
            TurnType.RIGHT -> "Gira a destra"
        }

    @get:DrawableRes
    val iconRes: Int
        get() = when (turnType) {
            TurnType.LEFT -> R.drawable.ic_turn_left
            TurnType.RIGHT -> R.drawable.ic_turn_right
        }
}

enum class TurnType {
    LEFT, RIGHT;

    companion object {
        fun fromBRouterSymbol(sym: String?): TurnType? {
            return when (sym?.lowercase()) {
                "sharpleft", "turnleft", "left", "slightleft", "tl", "tu" -> LEFT
                "sharpright", "turnright", "right", "slightright", "tr" -> RIGHT
                else -> null
            }
        }
    }
}
