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
            TurnType.LEFT_SHARP -> "Svolta a sinistra decisa"
            TurnType.LEFT -> "Gira a sinistra"
            TurnType.LEFT_SLIGHT -> "Tieni la sinistra"
            TurnType.STRAIGHT -> "Vai dritto"
            TurnType.RIGHT_SLIGHT -> "Tieni la destra"
            TurnType.RIGHT -> "Gira a destra"
            TurnType.RIGHT_SHARP -> "Svolta a destra decisa"
            TurnType.U_TURN -> "Fai inversione"
        }

    @get:DrawableRes
    val iconRes: Int
        get() = when (turnType) {
            TurnType.LEFT_SHARP, TurnType.LEFT -> R.drawable.ic_turn_left
            TurnType.LEFT_SLIGHT -> R.drawable.ic_turn_left
            TurnType.RIGHT_SHARP, TurnType.RIGHT -> R.drawable.ic_turn_right
            TurnType.RIGHT_SLIGHT -> R.drawable.ic_turn_right
            TurnType.U_TURN -> R.drawable.ic_turn_u_turn
            TurnType.STRAIGHT -> R.drawable.ic_turn_straight
        }
}

enum class TurnType {
    LEFT_SHARP, LEFT, LEFT_SLIGHT, STRAIGHT, RIGHT_SLIGHT, RIGHT, RIGHT_SHARP, U_TURN;

    companion object {
        fun fromBRouterSymbol(sym: String?): TurnType? {
            return when (sym) {
                "SharpLeft" -> LEFT_SHARP
                "TurnLeft", "Left" -> LEFT
                "SlightLeft" -> LEFT_SLIGHT
                "Straight" -> STRAIGHT
                "SlightRight" -> RIGHT_SLIGHT
                "TurnRight", "Right" -> RIGHT
                "SharpRight" -> RIGHT_SHARP
                "U-turn" -> U_TURN
                else -> null
            }
        }
    }
}
