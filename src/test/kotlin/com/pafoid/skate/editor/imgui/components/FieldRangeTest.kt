package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.StreamingFocus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FieldRangeTest {
    @Test
    fun FieldRange_ReadsRangeAnnotationAndClamps() {
        val range = FieldRange.of(StreamingFocus::class.java.getDeclaredField("radiusScale"))
        assertTrue(range.bounded)
        assertEquals(0.05f, range.floatSpeed(0.1f))
        assertEquals(0.1f, range.clamp(-3f))
        assertEquals(10f, range.clamp(99f))
        assertEquals(2f, range.clamp(2f))
    }

    @Test
    fun FieldRange_UnannotatedFieldIsUnbounded() {
        val range = FieldRange.of(StreamingFocus::class.java.getDeclaredField("priority"))
        assertFalse(range.bounded)
        assertEquals(-500, range.clamp(-500))
        assertEquals(0.1f, range.floatSpeed(0.1f))
    }

    @Test
    fun FieldKind_DoubleAndArrayHaveKinds() {
        assertEquals(FieldKind.DoubleField, FieldKind.of(Double::class.javaPrimitiveType!!))
        assertEquals(FieldKind.ArrayOf(Int::class.javaPrimitiveType!!), FieldKind.of(IntArray::class.java))
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) // descriptor element access
    @Test
    fun RuntimeState_IsNotSerializedToScenes() {
        val names = listOf(InputStateComponent::class.java, DirectionalLightComponent::class.java).flatMap { c ->
            kotlinx.serialization.serializer(c).descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName) }
        }
        assertFalse("jumpPressed" in names || "moveDirection" in names || "lightSpaceMatrix" in names)
        assertTrue("intensity" in names)
    }
}
