package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.PROJECTION
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms.VIEW
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Draws the infinite editor grid (REN-25) with `editor_grid.glsl`: one full-screen quad whose pixels intersect their
 * view ray with the grid plane, so the grid has no edge and costs one draw. Spacing, anti-aliasing and fade follow
 * [GridMath]. Drawn by the debug pass into the presented target, depth-tested against the scene without writing depth.
 */
class GridRenderer(
    private val shader: Shader,
    private val cameraManager: CameraManager,
) {
    /** One grid plane to draw: height, spacing, colours and the extent range of its distance fade. */
    data class Plane(
        val y: Float,
        val minorStep: Float,
        val majorStep: Float,
        val minorColor: Vector3f,
        val majorColor: Vector3f,
        val minExtent: Float,
        val maxExtent: Float,
        val showAxes: Boolean,
    )

    private var buffer: RhiBuffer? = null
    private var input: RhiVertexInput? = null
    private val pipelines = PipelineCache(PipelineStates.GRID)
    private val viewProjection = Matrix4f()
    private val inverseViewProjection = Matrix4f()
    private val cameraPosition = Vector3f()

    /** Draws [plane]; the caller has begun the presented target's pass. [pixelAngle]: radians per pixel. */
    fun draw(plane: Plane, pixelAngle: Float) {
        val quad = quad() ?: return
        val camera = cameraManager.camera
        camera.projection.mul(camera.view, viewProjection).invert(inverseViewProjection)
        val camPos = cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        val fadeEnd = GridMath.fadeEnd(camPos.y - plane.y, plane.minExtent, plane.maxExtent)

        shader.start()
        shader.uploadMat4f(INV_VIEW_PROJ, inverseViewProjection)
        shader.uploadMat4f(VIEW, camera.view)
        shader.uploadMat4f(PROJECTION, camera.projection)
        shader.uploadVec3f(CAMERA_POS, camPos)
        shader.uploadFloat(PIXEL_ANGLE, pixelAngle)
        shader.uploadFloat(GRID_Y, plane.y)
        shader.uploadFloat(MINOR_STEP, plane.minorStep)
        shader.uploadFloat(LOD_RATIO, GridMath.ratio(plane.minorStep, plane.majorStep))
        shader.uploadFloat(MIN_PIXELS, GridMath.MIN_PIXELS_BETWEEN_LINES)
        shader.uploadVec3f(MINOR_COLOR, plane.minorColor)
        shader.uploadVec3f(MAJOR_COLOR, plane.majorColor)
        shader.uploadFloat(MINOR_OPACITY, GridMath.MINOR_OPACITY)
        shader.uploadFloat(MAJOR_OPACITY, GridMath.MAJOR_OPACITY)
        shader.uploadFloat(MINOR_WIDTH, GridMath.MINOR_LINE_WIDTH_PX)
        shader.uploadFloat(MAJOR_WIDTH, GridMath.MAJOR_LINE_WIDTH_PX)
        shader.uploadFloat(AXIS_WIDTH, GridMath.AXIS_LINE_WIDTH_PX)
        shader.uploadInt(SHOW_AXES, if (plane.showAxes) 1 else 0)
        shader.uploadFloat(FADE_START, GridMath.fadeStart(fadeEnd))
        shader.uploadFloat(FADE_END, fadeEnd)

        val cmd = RhiContext.require().commandList()
        pipelines.get(shader, quad.layout)?.let { pipeline ->
            cmd.setPipeline(pipeline)
            cmd.setVertexInput(quad)
            cmd.draw(QUAD.size / 2)
        }
        shader.stop()
    }

    private fun quad(): RhiVertexInput? {
        input?.let { return it }
        val device = RhiContext.device ?: return null
        val vertices = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, QUAD)
        buffer = vertices
        return device.createVertexInput(device.createVertexLayout(VertexLayouts.FULLSCREEN_2D), listOf(vertices)).also { input = it }
    }

    fun destroy() {
        input?.close()
        buffer?.close()
        input = null
        buffer = null
        pipelines.close()
        shader.destroy()
    }

    private companion object {
        /** Two triangles covering the screen in NDC. */
        val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)

        const val INV_VIEW_PROJ = "uInvViewProj"
        const val CAMERA_POS = "uCameraPos"
        const val PIXEL_ANGLE = "uPixelAngle"
        const val GRID_Y = "uGridY"
        const val MINOR_STEP = "uMinorStep"
        const val LOD_RATIO = "uLodRatio"
        const val MIN_PIXELS = "uMinPixels"
        const val MINOR_COLOR = "uMinorColor"
        const val MAJOR_COLOR = "uMajorColor"
        const val MINOR_OPACITY = "uMinorOpacity"
        const val MAJOR_OPACITY = "uMajorOpacity"
        const val MINOR_WIDTH = "uMinorWidth"
        const val MAJOR_WIDTH = "uMajorWidth"
        const val AXIS_WIDTH = "uAxisWidth"
        const val SHOW_AXES = "uShowAxes"
        const val FADE_START = "uFadeStart"
        const val FADE_END = "uFadeEnd"
    }
}
