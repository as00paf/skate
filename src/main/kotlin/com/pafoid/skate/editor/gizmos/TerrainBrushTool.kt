package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.terrain.SetTerrainStampsCommand
import com.pafoid.skate.editor.commands.terrain.TerrainStrokeCommand
import com.pafoid.skate.editor.commands.terrain.UsePaintedBiomesCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.terrain.TerrainBrush
import com.pafoid.skate.editor.terrain.TerrainEditRecorder
import com.pafoid.skate.editor.terrain.TerrainEditing
import com.pafoid.skate.editor.terrain.TerrainStampHandles
import com.pafoid.skate.editor.terrain.TerrainStampHandles.Handle
import com.pafoid.skate.editor.terrain.TerrainTool
import com.pafoid.skate.editor.terrain.TerrainToolState
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.terrain.BiomeSource
import com.pafoid.skate.engine.terrain.TerrainGenerator
import com.pafoid.skate.engine.terrain.TerrainStamp
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The terrain tools in the viewport (EDT-92): the sculpt and paint brushes and the stamp handles, for the tool chosen
 * in the terrain inspector ([TerrainToolState]). Runs instead of the other gizmos while it [claims] the selection.
 *
 * - A brush stroke (mouse down to up) edits the chunks under the brush every frame and becomes one
 *   [TerrainStrokeCommand]; the terrain asset is created on the first stroke if needed and saved after each stroke.
 * - Painting first switches the terrain to painted biomes ([UsePaintedBiomesCommand]) and waits for the rebuild.
 * - Stamps: click the ground to add one, click a footprint to select it, drag a handle to move, resize or rotate;
 *   each drag is one [SetTerrainStampsCommand], applied on release (a rebuild per drag, not per frame).
 */
class TerrainBrushTool(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
    private val state: TerrainToolState = TerrainToolState.shared,
    private val editing: () -> TerrainEditing? = { TerrainEditing.active },
) : Gizmo(engine, undoRedoManager) {

    private var recorder: TerrainEditRecorder? = null
    private var strokeTerrain: TerrainComponent? = null
    private var strokeGenerator: TerrainGenerator? = null
    private var strokeTool = TerrainTool.NONE
    private var flattenTo = 0f
    private var waitingForPaintedBiomes = false

    private var dragIndex = -1
    private var dragHandle: Handle? = null
    private var dragPreview: TerrainStamp? = null

    private val origin = Vector3f()
    private val localOrigin = Vector3f()
    private val hit = Vector3f()

    /** The terrain the tool acts on: a tool is chosen and the inspected terrain's object is the only selection. */
    fun claims(selection: List<GameObject>): TerrainComponent? {
        if (state.tool == TerrainTool.NONE) return null
        val terrain = state.terrain ?: return null
        val go = selection.singleOrNull() ?: return null
        return terrain.takeIf { go.getComponent<TerrainComponent>() === it && it.enabled }
    }

    /** One editor frame on [terrain] seen through [camera] at [cameraPosition]. */
    fun update(terrain: TerrainComponent, camera: CameraComponent, cameraPosition: Vector3f, dt: Float) {
        val go = terrain.gameObject ?: return
        go.getComponent<Transform>()?.worldMatrix?.getTranslation(origin) ?: origin.set(0f)
        if (inputProvider.keyBeginPress(GLFW_KEY_LEFT_BRACKET)) state.radius = state.radius * 0.8f
        if (inputProvider.keyBeginPress(GLFW_KEY_RIGHT_BRACKET)) state.radius = state.radius * 1.25f

        val ray = inputProvider.screenToRay(camera)
        localOrigin.set(ray.origin).sub(origin)
        val runtime = terrain.runtime
        val layout = runtime.layout
        val field = runtime.heightField
        val ground = if (layout != null && field != null && inputProvider.isInsideViewport()) {
            TerrainBrush.raycast(field, layout, localOrigin, ray.direction, out = hit)
        } else null

        if (state.tool == TerrainTool.STAMP) {
            endStroke()
            updateStamps(terrain, ray.direction, ground, cameraPosition)
        } else {
            cancelStampDrag()
            updateBrush(terrain, ground, dt)
        }
    }

    // ---------------------------------------------------------------------------------------------------------- brush

    private fun updateBrush(terrain: TerrainComponent, ground: Vector3f?, dt: Float) {
        val runtime = terrain.runtime
        ground?.let { drawCircle(terrain, it, state.radius, brushColor(state.tool)) }
        val held = inputProvider.isLeftMouseButtonDown(true)
        if (recorder == null) {
            if (ground == null || !inputProvider.isInsideViewport() || !inputProvider.leftMouseButtonBeginPress()) return
            beginStroke(terrain, ground)
        }
        if (!held || strokeTerrain !== terrain || state.tool != strokeTool) {
            endStroke()
            return
        }
        if (runtime.generator !== strokeGenerator) {
            // The terrain rebuilt under the stroke (settings, source switch): the snapshots no longer match its chunks
            if (waitingForPaintedBiomes && runtime.key?.biomeSource == BiomeSource.PAINTED) {
                waitingForPaintedBiomes = false
                strokeGenerator = runtime.generator
            } else {
                recorder = null
                strokeTerrain = null
                return
            }
        }
        if (waitingForPaintedBiomes || ground == null) return
        val r = recorder ?: return
        val changed = if (strokeTool == TerrainTool.PAINT_BIOME) {
            TerrainBrush.paint(runtime, ground.x, ground.z, state.radius, state.biome, r)
        } else {
            TerrainBrush.sculpt(runtime, strokeTool, ground.x, ground.z, state.radius, state.strength, dt, flattenTo, r)
        }
        if (changed > 0) runtime.markEdited()
    }

    private fun beginStroke(terrain: TerrainComponent, ground: Vector3f) {
        val edit = editing()
        edit?.ensureAsset(terrain)
        strokeTerrain = terrain
        strokeTool = state.tool
        flattenTo = ground.y
        recorder = TerrainEditRecorder()
        strokeGenerator = terrain.runtime.generator
        waitingForPaintedBiomes = false
        if (strokeTool == TerrainTool.PAINT_BIOME && terrain.biomeSource != BiomeSource.PAINTED) {
            val save: (TerrainComponent) -> Unit = { t -> edit?.save(t) }
            undoRedoManager.executeCommand(UsePaintedBiomesCommand(terrain, save))
            waitingForPaintedBiomes = true
        }
    }

    /** Ends the current stroke: one undoable command for everything it changed, then the asset is saved. */
    fun endStroke() {
        val r = recorder ?: return
        val terrain = strokeTerrain
        recorder = null
        strokeTerrain = null
        waitingForPaintedBiomes = false
        if (terrain == null || r.isEmpty) return
        val edit = editing()
        val save: (TerrainComponent) -> Unit = { t -> edit?.save(t) }
        undoRedoManager.pushCommand(TerrainStrokeCommand(terrain, r.before, r.after(terrain.runtime), strokeTool == TerrainTool.PAINT_BIOME, save))
        edit?.save(terrain)
        sceneManager.currentScene?.isDirty = true
    }

    // --------------------------------------------------------------------------------------------------------- stamps

    private fun updateStamps(terrain: TerrainComponent, direction: Vector3f, ground: Vector3f?, cameraPosition: Vector3f) {
        val stamps = terrain.stamps
        if (state.selectedStamp !in stamps.indices) state.selectedStamp = -1
        stamps.forEachIndexed { index, s ->
            val shown = if (index == dragIndex) dragPreview ?: s else s
            drawOutline(shown, if (index == state.selectedStamp) SELECTED else STAMP)
            if (index == state.selectedStamp) TerrainStampHandles.handles(shown).forEach { (_, p) -> drawHandle(p) }
        }

        val selected = stamps.getOrNull(state.selectedStamp)
        if (dragIndex < 0) {
            if (!inputProvider.isInsideViewport() || !inputProvider.leftMouseButtonBeginPress()) return
            // A handle of the selected stamp, on the plane at its height
            if (selected != null) {
                val p = planePoint(direction, selected.y)
                val tolerance = max(0.4f, cameraPosition.distance(Vector3f(p).add(origin)) * 0.02f)
                val handle = TerrainStampHandles.pick(selected, p, tolerance)
                if (handle != null) { startDrag(state.selectedStamp, handle, selected); return }
            }
            val g = ground ?: return
            // A footprint under the cursor selects that stamp and starts moving it
            val inside = stamps.indexOfFirst { it.distanceOutside(g.x, g.z) == 0f }
            if (inside >= 0) {
                state.selectedStamp = inside
                startDrag(inside, Handle.CENTER, stamps[inside])
                return
            }
            // Empty ground: a new stamp (RECT 10 × 10 m, FLATTEN) at the clicked height
            val added = TerrainStamp(x = g.x, y = g.y, z = g.z)
            undoRedoManager.executeCommand(SetTerrainStampsCommand(terrain, stamps + added))
            state.selectedStamp = terrain.stamps.size - 1
            sceneManager.currentScene?.isDirty = true
            return
        }

        val original = stamps.getOrNull(dragIndex)
        val handle = dragHandle
        if (original == null || handle == null) { cancelStampDrag(); return }
        dragPreview = TerrainStampHandles.drag(original, handle, planePoint(direction, original.y))
        if (inputProvider.isLeftMouseButtonDown(true)) return
        val result = dragPreview
        cancelStampDrag()
        if (result != null && result != original) {
            val next = stamps.toMutableList().also { it[stamps.indexOf(original)] = result }
            undoRedoManager.executeCommand(SetTerrainStampsCommand(terrain, next))
            sceneManager.currentScene?.isDirty = true
        }
    }

    private fun startDrag(index: Int, handle: Handle, stamp: TerrainStamp) {
        dragIndex = index
        dragHandle = handle
        dragPreview = stamp
    }

    private fun cancelStampDrag() {
        dragIndex = -1
        dragHandle = null
        dragPreview = null
    }

    /** Where the mouse ray meets the horizontal plane at terrain-local height [y] (terrain-local XZ). */
    private fun planePoint(direction: Vector3f, y: Float): Vector3f {
        val dy = direction.y
        val t = if (abs(dy) < 1e-5f) 0f else (y - localOrigin.y) / dy
        return Vector3f(direction).mul(max(t, 0f)).add(localOrigin).also { it.y = y }
    }

    // ---------------------------------------------------------------------------------------------------------- draw

    private fun drawCircle(terrain: TerrainComponent, center: Vector3f, radius: Float, color: Vector3f) {
        val field = terrain.runtime.heightField ?: return
        var prev: Vector3f? = null
        for (k in 0..SEGMENTS) {
            val a = 2.0 * Math.PI * k / SEGMENTS
            val x = center.x + (cos(a) * radius).toFloat()
            val z = center.z + (sin(a) * radius).toFloat()
            val p = Vector3f(x, field.heightAt(x, z) + LIFT, z).add(origin)
            prev?.let { debugRenderer.addLine3D(it, p, color) }
            prev = p
        }
        debugRenderer.addLine3D(Vector3f(center).add(origin), Vector3f(center).add(origin).add(0f, max(0.5f, radius * 0.2f), 0f), color)
    }

    private fun drawOutline(stamp: TerrainStamp, color: Vector3f) {
        val points = TerrainStampHandles.outline(stamp)
        for (k in 1 until points.size) {
            debugRenderer.addLine3D(Vector3f(points[k - 1]).add(origin).add(0f, LIFT, 0f), Vector3f(points[k]).add(origin).add(0f, LIFT, 0f), color)
        }
    }

    private fun drawHandle(local: Vector3f) {
        val p = Vector3f(local).add(origin).add(0f, LIFT, 0f)
        val s = HANDLE_SIZE
        debugRenderer.addLine3D(Vector3f(p).add(-s, 0f, 0f), Vector3f(p).add(s, 0f, 0f), HANDLE)
        debugRenderer.addLine3D(Vector3f(p).add(0f, 0f, -s), Vector3f(p).add(0f, 0f, s), HANDLE)
        debugRenderer.addLine3D(p, Vector3f(p).add(0f, s * 2f, 0f), HANDLE)
    }

    companion object {
        const val SEGMENTS = 48
        const val LIFT = 0.05f
        const val HANDLE_SIZE = 0.4f
        private val STAMP = Vector3f(0.6f, 0.6f, 0.6f)
        private val SELECTED = Vector3f(1f, 0.6f, 0.1f)
        private val HANDLE = Vector3f(1f, 1f, 0.2f)

        fun brushColor(tool: TerrainTool): Vector3f = when (tool) {
            TerrainTool.RAISE -> Vector3f(0.3f, 1f, 0.3f)
            TerrainTool.LOWER -> Vector3f(1f, 0.35f, 0.3f)
            TerrainTool.SMOOTH -> Vector3f(0.35f, 0.6f, 1f)
            TerrainTool.FLATTEN -> Vector3f(1f, 0.9f, 0.3f)
            else -> Vector3f(1f, 0.4f, 1f)
        }
    }
}
