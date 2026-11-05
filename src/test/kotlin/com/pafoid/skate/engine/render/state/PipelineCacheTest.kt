package com.pafoid.skate.engine.render.state

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.rhi.BlendState
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.Topology
import com.pafoid.skate.engine.render.data.VertexLayouts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineCacheTest {
    private val device = FakeRhiDevice()
    private val cache = PipelineCache(PipelineStates.SPRITES) { device }
    private fun shader(name: String) = Shader(device.createShader(ShaderDesc(name, "v", "f")))

    @Test
    fun Get_CreatesOncePerShaderAndLayout() {
        val a = shader("a")
        val b = shader("b")
        val l1 = device.createVertexLayout(VertexLayouts.BATCH_2D)
        val l2 = device.createVertexLayout(VertexLayouts.DEBUG)
        val p = cache.get(a, l1)!!
        assertSame(p, cache.get(a, l1))
        assertNotSame(p, cache.get(a, l2))
        assertNotSame(p, cache.get(b, l1))
        assertEquals(3, device.pipelines.size)
        assertSame(a.rhi, p.desc.shader)
        assertEquals(PipelineStates.SPRITES, p.desc.state)
    }

    @Test
    fun HotReload_RebuildsTheShadersPipelines() {
        val a = shader("a")
        val layout = device.createVertexLayout(VertexLayouts.BATCH_2D)
        val before = cache.get(a, layout)!!
        a.replaceWith(shader("a2"))
        val after = cache.get(a, layout)!!
        assertNotSame(before, after)
        assertTrue((before as FakeRhiDevice.Pipeline).closed)
        assertSame(a.rhi, after.desc.shader)
    }

    @Test
    fun DestroyedShader_HasNoPipeline_CloseFreesAll() {
        val a = shader("a")
        val layout = device.createVertexLayout(VertexLayouts.BATCH_2D)
        val p = cache.get(a, layout) as FakeRhiDevice.Pipeline
        cache.close()
        assertTrue(p.closed)
        a.destroy()
        assertNull(cache.get(a, layout))
    }

    @Test
    fun States_MatchWhatEachDrawUsedToInherit() {
        // REN-19: every blended draw keeps the target alpha opaque
        for (s in listOf(PipelineStates.MESH_BLENDED, PipelineStates.PARTICLES, PipelineStates.UI)) {
            assertEquals(BlendState.ALPHA_KEEP_TARGET_OPAQUE, s.blend)
        }
        assertFalse(PipelineStates.MESH_BLENDED.depth.write)
        assertEquals(CullMode.BACK, PipelineStates.mesh(blended = false, doubleSided = false).cull)
        assertEquals(CullMode.NONE, PipelineStates.mesh(blended = true, doubleSided = true).cull)
        assertEquals(Topology.TRIANGLE_STRIP, PipelineStates.PARTICLES_ADDITIVE.topology)
        assertTrue(PipelineStates.SHADOW.depth.clamp)
        assertEquals(CullMode.NONE, PipelineStates.SHADOW.cull)
        assertFalse(PipelineStates.FULLSCREEN.depth.test)
    }
}
