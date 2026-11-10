package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * EDT-54 (R2): Geometry renders unshadowed when the shadow pass did not publish this frame. Nothing to clear, no
 * stale maps: the shadow pass publishes its maps, the geometry pass consumes them once.
 */
class GeometryShadowHandoffTest {
    /** Stand-in for ShadowPass's bookkeeping: reset, render, publish (ShadowPass.execute). */
    private class PublishingShadowPass(val frame: ShadowFrame) : BaseRenderPass() {
        override val name = "ShadowPass"
        override fun execute(scene: Scene) {
            frame.reset()
            frame.cascadeCount = 2
            frame.publish()
        }
    }

    /** Records what the geometry pass would hand to the lighting uniforms each frame. */
    private class GeometryProbe(val frame: ShadowFrame) : BaseRenderPass() {
        override val name = "GeometryPass"
        val seen = ArrayList<ShadowFrame?>()
        override fun execute(scene: Scene) { seen += GeometryPass.shadowsThisFrame(frame) }
    }

    @Test
    fun Geometry_RendersUnshadowed_WhenTheShadowPassDidNotPublishThisFrame() {
        val frame = ShadowFrame()
        val geometry = GeometryProbe(frame)
        val graph = RenderGraph { null }.apply {
            addPass(PublishingShadowPass(frame))
            addPass(geometry)
        }

        graph.execute(Scene())                  // shadows on: published, used
        graph.setEnabled("ShadowPass", false)
        graph.execute(Scene())                  // shadows off: nothing published, last frame's maps not reused
        graph.execute(Scene())
        graph.setEnabled("ShadowPass", true)
        graph.execute(Scene())

        assertSame(frame, geometry.seen[0])
        assertNull(geometry.seen[1])
        assertNull(geometry.seen[2])
        assertSame(frame, geometry.seen[3])
        assertEquals(4, geometry.seen.size)
    }

    @Test
    fun NoShadowPass_NeverPublishes() {
        val frame = ShadowFrame()
        val geometry = GeometryProbe(frame)
        RenderGraph { null }.apply {
            addPass(NoShadowPass())
            addPass(geometry)
        }.execute(Scene())
        assertNull(geometry.seen.single())
    }

    @Test
    fun NoShadows_UploadsZeroShadowCounts() {
        val shader = mockk<Shader>(relaxed = true)
        LightingUniformsLoader().loadShadowUniforms(shader, GeometryPass.shadowsThisFrame(ShadowFrame()))
        verify { shader.uploadInt(Uniforms.CASCADE_COUNT, 0) }
        verify { shader.uploadInt(Uniforms.SHADOWED_LIGHT_COUNT, 0) }
    }
}
