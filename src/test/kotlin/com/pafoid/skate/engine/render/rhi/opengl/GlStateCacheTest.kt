package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.BlendFactor
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.state.PipelineStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GlStateCache issues only the GL calls that change state (REN-12 step 6). */
class GlStateCacheTest {
    private val calls = ArrayList<String>()
    private val cache = GlStateCache(object : GlStateOps {
        override fun enable(cap: GlCap, on: Boolean) { calls += "${if (on) "enable" else "disable"} $cap" }
        override fun depthFunc(func: CompareFunc) { calls += "depthFunc $func" }
        override fun depthMask(write: Boolean) { calls += "depthMask $write" }
        override fun blendFunc(srcRgb: BlendFactor, dstRgb: BlendFactor, srcAlpha: BlendFactor, dstAlpha: BlendFactor) {
            calls += "blend $srcRgb $dstRgb $srcAlpha $dstAlpha"
        }
        override fun cullFace(mode: CullMode) { calls += "cull $mode" }
        override fun polygonOffset(factor: Float, units: Float) { calls += "offset $factor $units" }
        override fun lineWidth(width: Float) { calls += "lineWidth $width" }
        override fun scissor(x: Int, y: Int, width: Int, height: Int) { calls += "scissor $x $y $width $height" }
    })

    @Test
    fun Apply_FromUnknown_SetsEverythingThenOnlyDifferences() {
        cache.apply(PipelineStates.MESH_OPAQUE)
        assertEquals(
            listOf(
                "enable DEPTH_TEST", "depthFunc LESS", "depthMask true", "disable DEPTH_CLAMP", "disable BLEND",
                "enable CULL_FACE", "cull BACK", "disable POLYGON_OFFSET_FILL",
            ),
            calls,
        )
        calls.clear()
        cache.apply(PipelineStates.MESH_OPAQUE)
        assertEquals(emptyList<String>(), calls, "same pipeline again: no GL call")

        cache.apply(PipelineStates.MESH_BLENDED_DOUBLE_SIDED)
        assertEquals(
            listOf("depthMask false", "enable BLEND", "blend SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA", "disable CULL_FACE"),
            calls,
        )
    }

    @Test
    fun Apply_ShadowAndLines_SetOffsetClampAndWidthOnce() {
        cache.apply(PipelineStates.SHADOW)
        calls.clear()
        cache.apply(PipelineStates.DEBUG_LINES)
        assertEquals(
            listOf("depthFunc LESS_EQUAL", "disable DEPTH_CLAMP", "disable POLYGON_OFFSET_FILL", "lineWidth 4.0"),
            calls,
        )
        calls.clear()
        cache.apply(PipelineStates.SHADOW)
        assertEquals(
            listOf("depthFunc LESS", "enable DEPTH_CLAMP", "enable POLYGON_OFFSET_FILL"),
            calls,
            "offset values and line width are remembered",
        )
    }

    @Test
    fun BlendFactors_WhileBlendingIsOff_AreLeftAlone() {
        cache.apply(PipelineStates.PARTICLES_ADDITIVE)
        cache.apply(PipelineStates.FULLSCREEN)
        calls.clear()
        cache.apply(PipelineStates.PARTICLES_ADDITIVE)
        assertEquals(listOf("enable DEPTH_TEST", "enable BLEND"), calls.filterNot { it.startsWith("depth") || it.startsWith("disable") })
    }

    @Test
    fun Invalidate_ForgetsEverything_AndScissorDiffs() {
        cache.setScissor(1, 2, 3, 4)
        cache.setScissor(1, 2, 3, 4)
        cache.disableScissor()
        cache.setScissor(1, 2, 3, 4)
        assertEquals(listOf("enable SCISSOR_TEST", "scissor 1 2 3 4", "disable SCISSOR_TEST", "enable SCISSOR_TEST"), calls)

        cache.apply(PipelineStates.UI)
        cache.invalidate()
        calls.clear()
        cache.apply(PipelineStates.UI)
        assertEquals(7, calls.size, "every switch and the blend factors are set again: $calls")
    }
}
