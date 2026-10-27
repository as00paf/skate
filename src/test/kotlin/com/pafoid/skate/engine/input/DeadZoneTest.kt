package com.pafoid.skate.engine.input

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class DeadZoneTest {
    private val out = Vector2f()

    @Test
    fun DeadZone_RescaleInnerOuterAndEndpoints() {
        assertEquals(0f, DeadZoneMath.rescale(0.1f, 0.2f, 0.1f, 1f))
        assertEquals(1f, DeadZoneMath.rescale(0.95f, 0.2f, 0.1f, 1f))
        // live range 0.2..0.9, midpoint 0.55 -> 0.5
        assertEquals(0.5f, DeadZoneMath.rescale(0.55f, 0.2f, 0.1f, 1f), 1e-5f)
    }

    @Test
    fun DeadZone_ResponseCurveBendsLiveRange() {
        assertEquals(0.25f, DeadZoneMath.rescale(0.5f, 0f, 0f, 2f), 1e-5f)
        assertEquals(0.5f, DeadZoneMath.rescale(0.5f, 0f, 0f, 1f), 1e-5f)
    }

    @Test
    fun DeadZone_AxialFiltersEachAxisIndependently() {
        DeadZoneMath.axial(0.1f, 0.8f, 0.2f, 0f, 1f, out)
        assertEquals(0f, out.x)
        assertEquals(0.75f, out.y, 1e-5f)
        DeadZoneMath.axial(-0.6f, -0.1f, 0.2f, 0f, 1f, out)
        assertEquals(-0.5f, out.x, 1e-5f)
        assertEquals(0f, out.y)
    }

    @Test
    fun DeadZone_RadialKeepsDirectionAndUsesMagnitude() {
        // (0.15, 0.15) has magnitude 0.212: inside a 0.25 radial zone but would survive axial 0.1
        DeadZoneMath.radial(0.15f, 0.15f, 0.25f, 0f, 1f, out)
        assertEquals(0f, out.x); assertEquals(0f, out.y)
        DeadZoneMath.radial(0.3f, 0.4f, 0.2f, 0f, 1f, out)
        assertEquals(0.375f, out.length(), 1e-5f)
        assertEquals(0.3f / 0.4f, out.x / out.y, 1e-5f)
    }

    @Test
    fun DeadZone_RadialClampsCornerOvershootToUnitCircle() {
        DeadZoneMath.radial(1f, 1f, 0.1f, 0f, 1f, out)
        assertEquals(1f, out.length(), 1e-5f)
        assertEquals(1f / sqrt(2f), out.x, 1e-5f)
    }

    @Test
    fun DeadZone_StickOuterZoneSaturates() {
        val zone = StickDeadZone(mode = DeadZoneMode.RADIAL, inner = 0.1f, outer = 0.2f)
        zone.apply(0.85f, 0f, out)
        assertEquals(1f, out.x, 1e-5f)
    }

    @Test
    fun DeadZone_TriggerPressUsesProcessedValue() {
        val t = TriggerDeadZone(inner = 0.2f, outer = 0f, curve = 1f, pressThreshold = 0.5f)
        assertFalse(t.isPressed(0.5f)) // processed 0.375
        assertTrue(t.isPressed(0.7f)) // processed 0.625
        assertFalse(t.isPressed(-1f))
    }

    @Test
    fun DeadZone_ValidateClampsValues() {
        val s = DeadZoneSettings(leftStick = StickDeadZone(inner = 5f, outer = 5f, curve = 0f))
        s.validate()
        assertEquals(0.9f, s.leftStick.inner)
        assertEquals(0.5f, s.leftStick.outer)
        assertEquals(0.1f, s.leftStick.curve)
    }

    @Test
    fun DeadZone_SettingsPersistThroughMappingsJson() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val m = InputMappings()
        m.deadZones.leftStick = StickDeadZone(DeadZoneMode.AXIAL, 0.3f, 0.05f, 2f)
        m.deadZones.rightTrigger = TriggerDeadZone(pressThreshold = 0.8f)
        val loaded = json.decodeFromString<InputMappings>(json.encodeToString(m))
        assertEquals(m.deadZones, loaded.deadZones)
        // projects saved before dead zones existed still load with defaults
        assertEquals(DeadZoneSettings(), json.decodeFromString<InputMappings>("{}").deadZones)
    }
}
