package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.BlendFactor
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.rhi.PipelineState
import com.pafoid.skate.engine.render.rhi.Topology
import org.lwjgl.opengl.GL11.GL_BACK
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FRONT
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11.glCullFace
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glLineWidth
import org.lwjgl.opengl.GL11.glPolygonOffset
import org.lwjgl.opengl.GL11.glScissor
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP

/** Fixed-function switches the cache tracks. */
internal enum class GlCap { DEPTH_TEST, DEPTH_CLAMP, BLEND, CULL_FACE, POLYGON_OFFSET_FILL, SCISSOR_TEST }

/** The GL calls behind [GlStateCache]; injected so the diffing is unit-tested without a context. */
internal interface GlStateOps {
    fun enable(cap: GlCap, on: Boolean)
    fun depthFunc(func: CompareFunc)
    fun depthMask(write: Boolean)
    fun blendFunc(srcRgb: BlendFactor, dstRgb: BlendFactor, srcAlpha: BlendFactor, dstAlpha: BlendFactor)
    fun cullFace(mode: CullMode)
    fun polygonOffset(factor: Float, units: Float)
    fun lineWidth(width: Float)
    fun scissor(x: Int, y: Int, width: Int, height: Int)
}

/**
 * The context's fixed-function state as last set through it (docs/design/REN-12.md §4, formerly GLStateTracker).
 * [apply] issues only the calls that change something; [invalidate] forgets everything after code outside the RHI
 * (ImGui) may have changed state, so the next [apply] sets it all again. Every issued call counts as one
 * [EngineStats.stateChanges].
 */
internal class GlStateCache(private val gl: GlStateOps) {
    private val caps = IntArray(GlCap.entries.size) { UNKNOWN }
    private var depthFunc: CompareFunc? = null
    private var depthWrite = UNKNOWN
    private val blend = arrayOfNulls<BlendFactor>(4)
    private var cullMode: CullMode? = null
    private var offsetFactor = Float.NaN
    private var offsetUnits = Float.NaN
    private var lineWidth = Float.NaN
    private val scissor = IntArray(4) { UNKNOWN }

    fun invalidate() {
        caps.fill(UNKNOWN)
        depthFunc = null
        depthWrite = UNKNOWN
        blend.fill(null)
        cullMode = null
        offsetFactor = Float.NaN
        offsetUnits = Float.NaN
        lineWidth = Float.NaN
        scissor.fill(UNKNOWN)
    }

    /** Sets everything [state] specifies. Blend factors are left alone while blending is off (they have no effect). */
    fun apply(state: PipelineState) {
        val d = state.depth
        enable(GlCap.DEPTH_TEST, d.test)
        if (d.test) setDepthFunc(d.func)
        setDepthWrite(d.write)
        enable(GlCap.DEPTH_CLAMP, d.clamp)

        val b = state.blend
        enable(GlCap.BLEND, b.enabled)
        if (b.enabled) setBlend(b.srcRgb, b.dstRgb, b.srcAlpha, b.dstAlpha)

        enable(GlCap.CULL_FACE, state.cull != CullMode.NONE)
        if (state.cull != CullMode.NONE && cullMode != state.cull) {
            gl.cullFace(state.cull)
            cullMode = state.cull
            changed()
        }

        val offset = state.polygonOffset
        enable(GlCap.POLYGON_OFFSET_FILL, offset != null)
        if (offset != null && !(offset.factor == offsetFactor && offset.units == offsetUnits)) {
            gl.polygonOffset(offset.factor, offset.units)
            offsetFactor = offset.factor
            offsetUnits = offset.units
            changed()
        }

        if (state.topology == Topology.LINES && state.lineWidth != lineWidth) {
            gl.lineWidth(state.lineWidth)
            lineWidth = state.lineWidth
            changed()
        }
    }

    fun setDepthWrite(write: Boolean) {
        val v = if (write) 1 else 0
        if (depthWrite == v) return
        gl.depthMask(write)
        depthWrite = v
        changed()
    }

    /** Null disables the scissor test. */
    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        enable(GlCap.SCISSOR_TEST, true)
        if (scissor[0] == x && scissor[1] == y && scissor[2] == width && scissor[3] == height) return
        gl.scissor(x, y, width, height)
        scissor[0] = x; scissor[1] = y; scissor[2] = width; scissor[3] = height
        changed()
    }

    fun disableScissor() = enable(GlCap.SCISSOR_TEST, false)

    private fun enable(cap: GlCap, on: Boolean) {
        val v = if (on) 1 else 0
        if (caps[cap.ordinal] == v) return
        gl.enable(cap, on)
        caps[cap.ordinal] = v
        changed()
    }

    private fun setDepthFunc(func: CompareFunc) {
        if (depthFunc == func) return
        gl.depthFunc(func)
        depthFunc = func
        changed()
    }

    private fun setBlend(srcRgb: BlendFactor, dstRgb: BlendFactor, srcAlpha: BlendFactor, dstAlpha: BlendFactor) {
        if (blend[0] == srcRgb && blend[1] == dstRgb && blend[2] == srcAlpha && blend[3] == dstAlpha) return
        gl.blendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha)
        blend[0] = srcRgb; blend[1] = dstRgb; blend[2] = srcAlpha; blend[3] = dstAlpha
        changed()
    }

    private fun changed() {
        EngineStats.stateChanges.incrementAndGet()
    }

    private companion object {
        const val UNKNOWN = -1
    }
}

/** [GlStateOps] on the current LWJGL context. */
internal object LwjglStateOps : GlStateOps {
    override fun enable(cap: GlCap, on: Boolean) {
        val glCap = when (cap) {
            GlCap.DEPTH_TEST -> GL_DEPTH_TEST
            GlCap.DEPTH_CLAMP -> GL_DEPTH_CLAMP
            GlCap.BLEND -> GL_BLEND
            GlCap.CULL_FACE -> GL_CULL_FACE
            GlCap.POLYGON_OFFSET_FILL -> GL_POLYGON_OFFSET_FILL
            GlCap.SCISSOR_TEST -> GL_SCISSOR_TEST
        }
        if (on) glEnable(glCap) else glDisable(glCap)
    }

    override fun depthFunc(func: CompareFunc) = glDepthFunc(GlFormats.compare(func))
    override fun depthMask(write: Boolean) = glDepthMask(write)
    override fun blendFunc(srcRgb: BlendFactor, dstRgb: BlendFactor, srcAlpha: BlendFactor, dstAlpha: BlendFactor) =
        glBlendFuncSeparate(GlFormats.blend(srcRgb), GlFormats.blend(dstRgb), GlFormats.blend(srcAlpha), GlFormats.blend(dstAlpha))
    override fun cullFace(mode: CullMode) = glCullFace(if (mode == CullMode.FRONT) GL_FRONT else GL_BACK)
    override fun polygonOffset(factor: Float, units: Float) = glPolygonOffset(factor, units)
    override fun lineWidth(width: Float) = glLineWidth(width)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = glScissor(x, y, width, height)
}

/** The state cache of the (single) GL context. */
internal object GlState {
    val cache = GlStateCache(LwjglStateOps)
}
