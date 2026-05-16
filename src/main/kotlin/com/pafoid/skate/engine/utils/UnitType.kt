package com.pafoid.skate.engine.utils

sealed class UnitType {
    data object METERS : UnitType()
    data object CENTIMETERS : UnitType()
    data object INCHES : UnitType()
    data object FEET : UnitType()
    companion object {
        fun values(): Array<UnitType> {
            return arrayOf(METERS, CENTIMETERS, INCHES, FEET)
        }

        fun valueOf(value: String): UnitType {
            return when (value) {
                "METERS" -> METERS
                "CENTIMETERS" -> CENTIMETERS
                "INCHES" -> INCHES
                "FEET" -> FEET
                else -> throw IllegalArgumentException("No object com.pafoid.skate.engine.utils.UnitType.$value")
            }
        }
    }
}