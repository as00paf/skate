package com.pafoid.skate.engine.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnitsTest {
    private val epsilon = 0.0001

    @Test
    fun testToMeters() {
        assertEquals(1.0, Units.toMeters(1.0, UnitType.METERS), epsilon)
        assertEquals(1.0, Units.toMeters(100.0, UnitType.CENTIMETERS), epsilon)
        assertEquals(0.3048, Units.toMeters(12.0, UnitType.INCHES), epsilon)
        assertEquals(0.3048, Units.toMeters(1.0, UnitType.FEET), epsilon)
        
        // Test 1 inch to meters
        assertEquals(0.0254, Units.toMeters(1.0, UnitType.INCHES), epsilon)
    }

    @Test
    fun testFromMeters() {
        assertEquals(1.0, Units.fromMeters(1.0, UnitType.METERS), epsilon)
        assertEquals(100.0, Units.fromMeters(1.0, UnitType.CENTIMETERS), epsilon)
        assertEquals(12.0, Units.fromMeters(0.3048, UnitType.INCHES), epsilon)
        assertEquals(1.0, Units.fromMeters(0.3048, UnitType.FEET), epsilon)
        
        // Test 1 meter to inches
        assertEquals(1.0 / 0.0254, Units.fromMeters(1.0, UnitType.INCHES), epsilon)
    }
}
