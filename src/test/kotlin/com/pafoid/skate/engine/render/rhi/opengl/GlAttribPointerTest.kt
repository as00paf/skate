package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_INT
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.GL_STREAM_DRAW
import org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

class GlAttribPointerTest {

    @Test
    fun Particles_InstanceAttributesAdvancePerInstance() {
        val p = GlAttribPointer.of(VertexLayouts.PARTICLES)
        assertEquals(listOf(0, 1, 2, 3), p.map { it.location })
        assertEquals(listOf(0, 1, 1, 1), p.map { it.divisor })
        assertEquals(listOf(0, 1, 1, 1), p.map { it.binding })
        assertEquals(listOf(0L, 0L, 16L, 32L), p.map { it.offset })
        assertEquals(listOf(8, 48, 48, 48), p.map { it.stride })
    }

    @Test
    fun IntegerAttributes_UseIPointerWithGlInt() {
        val p = GlAttribPointer.of(VertexLayouts.mesh(listOf(6 to AttributeType.IVEC4, 7 to AttributeType.VEC4)))
        assertTrue(p[0].integer); assertEquals(GL_INT, p[0].glType); assertEquals(4, p[0].components)
        assertTrue(!p[1].integer); assertEquals(GL_FLOAT, p[1].glType)
    }

    @Test
    fun Batch2D_MatchesTheOldPointerCalls() {
        // Exactly what RenderBatch2D passed to glVertexAttribPointer before REN-12 step 2
        val p = GlAttribPointer.of(VertexLayouts.BATCH_2D)
        assertEquals(listOf(Triple(0, 2, 0L), Triple(1, 4, 8L), Triple(2, 2, 24L), Triple(3, 1, 32L), Triple(4, 1, 36L)),
            p.map { Triple(it.location, it.components, it.offset) })
        assertTrue(p.all { it.stride == 40 && it.divisor == 0 && it.glType == GL_FLOAT })
    }

    @Test
    fun Formats_MapUsageAndUploadTarget() {
        assertEquals(listOf(GL_STATIC_DRAW, GL_DYNAMIC_DRAW, GL_STREAM_DRAW), BufferUpdate.entries.map(GlFormats::usage))
        assertEquals(GL_ARRAY_BUFFER, GlFormats.uploadTarget(BufferUsage.VERTEX))
        assertEquals(GL_ARRAY_BUFFER, GlFormats.uploadTarget(BufferUsage.INSTANCE))
        assertEquals(GL_UNIFORM_BUFFER, GlFormats.uploadTarget(BufferUsage.UNIFORM))
        // Never GL_ELEMENT_ARRAY_BUFFER: that would rebind the index buffer of the bound VAO
        assertEquals(GL_COPY_WRITE_BUFFER, GlFormats.uploadTarget(BufferUsage.INDEX))
    }
}
