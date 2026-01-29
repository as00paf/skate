package com.pafoid.skate.engine.utils

object Units {
    const val M_TO_CM = 100.0
    const val IN_TO_M = 0.0254
    const val FT_TO_M = 0.3048

    fun toMeters(value: Double, unit: UnitType): Double {
        return when (unit) {
            UnitType.METERS -> value
            UnitType.CENTIMETERS -> value / M_TO_CM
            UnitType.INCHES -> value * IN_TO_M
            UnitType.FEET -> value * FT_TO_M
        }
    }

    fun fromMeters(value: Double, unit: UnitType): Double {
        return when (unit) {
            UnitType.METERS -> value
            UnitType.CENTIMETERS -> value * M_TO_CM
            UnitType.INCHES -> value / IN_TO_M
            UnitType.FEET -> value / FT_TO_M
        }
    }
}
