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
            TurnType.U_TURN -> "Fai inversione"
        }

    @get:DrawableRes
    val iconRes: Int
        get() = when (turnType) {
            TurnType.LEFT, TurnType.TSHL -> R.drawable.ic_turn_left
            TurnType.RIGHT, TurnType.TSHR -> R.drawable.ic_turn_right
            TurnType.TSLL -> R.drawable.ic_turn_slight_left
            TurnType.TSLR -> R.drawable.ic_turn_slight_right
            TurnType.U_TURN -> R.drawable.ic_turn_u_turn
        }
}

enum class TurnType {
    LEFT,          // TL, LEFT, L, SHARP LEFT
    RIGHT,         // TR, RIGHT, R, SHARP RIGHT
    TSHL,          // TSHL (Sharp Left)
    TSHR,          // TSHR (Sharp Right)
    TSLL,          // TSLL (Slight Left / Keep Left / KL)
    TSLR,          // TSLR (Slight Right / Keep Right / KR)
    U_TURN;        // TU, TRU, U-turn

    companion object {
        fun fromBRouterSymbol(sym: String?): TurnType? {
            if (sym.isNullOrBlank()) return null
            val clean = sym.trim().uppercase().replace(" ", "").replace("-", "").replace("_", "")
            return when {
                clean == "TL" || clean == "LEFT" || clean == "TURNLEFT" || clean == "L" -> LEFT
                clean == "TR" || clean == "RIGHT" || clean == "TURNRIGHT" || clean == "R" -> RIGHT
                clean == "TSHL" || clean == "SHARPLEFT" -> TSHL
                clean == "TSHR" || clean == "SHARPRIGHT" -> TSHR
                clean == "TSLL" || clean == "SLIGHTLEFT" || clean == "KEEPLEFT" || clean == "KL" -> TSLL
                clean == "TSLR" || clean == "SLIGHTRIGHT" || clean == "KEEPRIGHT" || clean == "KR" -> TSLR
                clean == "TU" || clean == "TRU" || clean == "UTURN" -> U_TURN
                else -> null
            }
        }
    }
}

class GpxNavigationManager {
    private var instructions: List<TurnInstruction> = emptyList()

    fun setInstructions(newInstructions: List<TurnInstruction>) {
        instructions = newInstructions
    }

    fun onLocationUpdate(
        currentLocation: GeoPoint,
        points: List<GeoPoint>,
        closestIndex: Int
    ): Pair<TurnInstruction?, Double> {
        if (instructions.isEmpty() || closestIndex < 0 || points.isEmpty()) {
            return Pair(null, 0.0)
        }
        val nextInstruction = instructions.firstOrNull { it.pointIndex > closestIndex }
            ?: return Pair(null, 0.0)

        val targetPoint = nextInstruction.geoPoint
            ?: points.getOrNull(nextInstruction.pointIndex)
            ?: return Pair(null, 0.0)

        val distMeters = currentLocation.distanceToAsDouble(targetPoint)
        return Pair(nextInstruction, distMeters)
    }

    fun clear() {
        instructions = emptyList()
    }
}
