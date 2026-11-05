package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VAOLoaderTest {
    private val device = FakeRhiDevice()
    private val loader = VAOLoader { device }

    private fun skinned() = loader.loadToVAO(
        positions = FloatArray(9), textureCoords = FloatArray(6), normals = FloatArray(9), indices = intArrayOf(0, 1, 2),
        joints = IntArray(12), weights = FloatArray(12),
    )

    @Test
    fun Mesh_OneBufferPerPresentAttribute_PlusIndices() {
        val part = skinned()
        val input = device.inputs.single()
        assertEquals(input.nativeHandle.toInt(), part.vaoId)
        assertEquals(listOf(0, 1, 2, 6, 7), part.enabledAttributes)
        assertEquals(3, part.vertexCount)
        val layout = input.layout.desc.bindings.map { it.attributes.single().let { a -> a.location to a.type } }
        assertEquals(listOf(0 to AttributeType.VEC3, 1 to AttributeType.VEC2, 2 to AttributeType.VEC3,
            6 to AttributeType.IVEC4, 7 to AttributeType.VEC4), layout)
        assertEquals(5, input.buffers.size)
        assertTrue(input.buffers.all { it.usage == BufferUsage.VERTEX })
        assertEquals(BufferUsage.INDEX, input.indices!!.usage)
        assertTrue((input.buffers[3] as FakeRhiDevice.Buffer).ints != null, "joints upload as integers")
    }

    @Test
    fun Meshes_WithTheSameAttributes_ShareOneLayout() {
        skinned(); skinned()
        loader.loadToVAO(FloatArray(6), 2)
        assertEquals(2, device.layoutsCreated.size)
    }

    @Test
    fun PositionsOnly_UsesTheGivenSizeAndNoIndices() {
        val part = loader.loadToVAO(FloatArray(8), 2)
        assertEquals(4, part.vertexCount)
        assertEquals(AttributeType.VEC2, device.inputs.single().layout.desc.bindings.single().attributes.single().type)
        assertNull(device.inputs.single().indices)
        assertThrows<IllegalArgumentException> { loader.loadToVAO(FloatArray(5), 5) }
    }

    @Test
    fun DeleteVAO_ClosesTheInputThenItsBuffers_Once() {
        val part = skinned()
        val input = device.inputs.single()
        loader.deleteVAO(part.vaoId)
        assertTrue(input.closed)
        assertTrue((input.buffers + input.indices!!).all { (it as FakeRhiDevice.Buffer).closed })
        assertEquals("close vao${input.nativeHandle}", device.events.first { it.startsWith("close") })
        val closes = device.events.count { it.startsWith("close") }
        loader.deleteVAO(part.vaoId)
        assertEquals(closes, device.events.count { it.startsWith("close") })
    }

    @Test
    fun CleanUp_ReleasesEveryMesh() {
        skinned(); loader.loadToVAO(FloatArray(3), 3)
        loader.cleanUp()
        assertTrue(device.inputs.all { it.closed })
        assertFalse(device.events.isEmpty())
    }
}
