package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.GridRenderer
import com.pafoid.skate.engine.render.renderer.GroundDepthPull
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.floor

/**
 * Debug visualization render pass.
 *
 * Renders the editor grid, debug lines, triangles, and other visualization aids (physics colliders, editor camera and
 * light visuals) after post-processing. They depth-test against the scene, so models hide what is behind them.
 *
 * The grid is the infinite shader grid of [GridRenderer] (REN-25): thin anti-aliased lines whose spacing adapts to
 * the camera distance, fading with distance, with the red X and blue Z axes drawn in the same shader. The green Y axis
 * is a debug ribbon.
 *
 * @param debugRenderer The debug renderer for drawing lines and shapes
 * @param grid Draws the editor grid; null draws none (tests without a shader)
 * @param bindTarget Binds the presented render target (the viewport FBO or the default framebuffer) and its
 * viewport. Called before drawing because earlier passes (shadow, picking) bind their own targets. Debug geometry is
 * drawn after post-processing, so it is not tonemapped, and it depth-tests against the scene depth that the geometry
 * pass left in that target.
 * @param endTarget Ends the pass [bindTarget] began.
 * @param targetHeight Height in pixels of the presented target, for the grid's depth pull.
 */
class DebugPass(
    private val debugRenderer: DebugRenderer,
    private val cameraManager: CameraManager,
    private val bindTarget: () -> Unit = {},
    private val targetHeight: () -> Int = { 1080 },
    private val endTarget: () -> Unit = {},
    private val grid: GridRenderer? = null,
) : BaseRenderPass() {

    override val name: String = "DebugPass"
    override val description: String = "Renders debug visualization (grid, physics, gizmos, etc.)"

    private val lineStart = Vector3f()
    private val lineEnd = Vector3f()
    private val camPos = Vector3f()
    private val yAxisColor = Vector3f(0.2f, 1f, 0.2f)

    /** Frees the grid's GPU resources. */
    fun destroy() {
        grid?.destroy()
    }

    override fun prepare() {
        debugRenderer.beginFrame()
    }

    override fun execute(scene: Scene) {
        bindTarget()
        // Pixel size drives the grid's grazing-angle depth pull (GroundDepthPull, REN-19)
        val camera = cameraManager.camera
        val pixelAngle = GroundDepthPull.pixelAngle(Math.toRadians(camera.fov.toDouble()).toFloat() * camera.zoom, targetHeight())
        debugRenderer.pixelAngle = pixelAngle
        scene.getComponent<GridLines>()?.let { renderGrid(scene, it, pixelAngle) }
        debugRenderer.draw()
        endTarget()
    }

    private fun renderGrid(scene: Scene, g: GridLines, pixelAngle: Float) {
        if (!g.showGrid || scene.isRunning) return
        cameraManager.transform.worldMatrix.getTranslation(camPos)
        val cameraDistance = abs(camPos.y)

        grid?.let { renderer ->
            renderer.draw(primaryPlane(g), pixelAngle)
            if (g.secondaryGridEnabled) renderer.draw(secondaryPlane(g), pixelAngle)
        }

        if (g.snapVisualizationEnabled) renderSnapVisualization(g.majorStep, g.snapMarkerColor, cameraDistance)
        if (g.showCenterMarker && cameraDistance < g.centerMarkerDistance) {
            renderCenterMarker(g.centerMarkerColor, g.centerMarkerDistance, cameraDistance)
        }
        if (g.showOriginAxes && cameraDistance < 50f) renderYAxis(g.originAxesThickness, cameraDistance)
    }

    /** Snap marker: a small cross at the major intersection under the camera, when close to the grid. */
    private fun renderSnapVisualization(majorStep: Float, color: Vector3f, cameraDistance: Float) {
        if (cameraDistance > 20f) return
        val snapX = floor(camPos.x / majorStep) * majorStep
        val snapZ = floor(camPos.z / majorStep) * majorStep
        val size = 0.3f
        debugRenderer.addLine3D(lineStart.set(snapX - size, 0f, snapZ), lineEnd.set(snapX + size, 0f, snapZ), color)
        debugRenderer.addLine3D(lineStart.set(snapX, 0f, snapZ - size), lineEnd.set(snapX, 0f, snapZ + size), color)
    }

    /** Crosshair at the world origin, shrinking as the camera rises. */
    private fun renderCenterMarker(color: Vector3f, markerDistance: Float, cameraDistance: Float) {
        val size = (2.0f * (markerDistance - cameraDistance) / markerDistance).coerceIn(0.5f, 2.0f)
        debugRenderer.addGroundLine3D(lineStart.set(-size, 0f, 0f), lineEnd.set(size, 0f, 0f), color)
        debugRenderer.addGroundLine3D(lineStart.set(0f, 0f, -size), lineEnd.set(0f, 0f, size), color)
    }

    /** Green Y axis straight up from the origin (the X and Z axes are drawn by the grid shader). */
    private fun renderYAxis(thickness: Float, cameraDistance: Float) {
        val length = (100f * (50f - cameraDistance) / 50f).coerceIn(5f, 100f)
        debugRenderer.addThickLineQuad3D(Vector3f(0f, 0f, 0f), Vector3f(0f, length, 0f), yAxisColor, thickness)
    }

    companion object {
        /** The grid plane of a [GridLines] component, with the X/Z axes when it shows origin axes. */
        fun primaryPlane(g: GridLines) = GridRenderer.Plane(
            y = g.gridYOffset, minorStep = g.minorStep, majorStep = g.majorStep,
            minorColor = g.minorColor, majorColor = g.majorColor,
            minExtent = g.minExtent, maxExtent = g.maxExtent, showAxes = g.showOriginAxes,
        )

        /** The optional second plane at [GridLines.secondaryGridY], in its own colour, without axes. */
        fun secondaryPlane(g: GridLines) = GridRenderer.Plane(
            y = g.secondaryGridY, minorStep = g.minorStep, majorStep = g.majorStep,
            minorColor = Vector3f(g.secondaryGridColor).mul(0.5f), majorColor = g.secondaryGridColor,
            minExtent = g.minExtent, maxExtent = g.maxExtent, showAxes = false,
        )
    }
}
