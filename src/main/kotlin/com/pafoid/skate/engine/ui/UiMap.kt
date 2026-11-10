package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

/** A world area seen from above: X to the right, Z downwards (so -Z, "north", is at the top of the map). */
data class MapBounds(val minX: Float, val minZ: Float, val maxX: Float, val maxZ: Float) {
    val width: Float get() = maxX - minX
    val depth: Float get() = maxZ - minZ

    companion object {
        /** A square [size] metres across centred on ([x], [z]): the minimap's view around the player. */
        fun around(x: Float, z: Float, size: Float) = MapBounds(x - size / 2f, z - size / 2f, x + size / 2f, z + size / 2f)
    }
}

/**
 * World <-> map conversion (GUI-13). [bounds] are fitted into a map of [mapWidth] x [mapHeight] UI units with one
 * uniform scale (no stretching) and centred, so a 60 x 40 m floor in a square minimap leaves bands above and below.
 * Map coordinates have their origin at the map's top-left corner; world -Z is up.
 */
class MapTransform(val bounds: MapBounds, val mapWidth: Float, val mapHeight: Float) {
    /** UI units per world metre. */
    val scale: Float = if (bounds.width <= 0f || bounds.depth <= 0f) 0f else min(mapWidth / bounds.width, mapHeight / bounds.depth)

    /** Where the bounds start inside the map (the letterbox bands). */
    val offsetX: Float = (mapWidth - bounds.width * scale) / 2f
    val offsetY: Float = (mapHeight - bounds.depth * scale) / 2f

    /** The part of the map that shows [bounds]. */
    val area: UiRectF get() = UiRectF(offsetX, offsetY, bounds.width * scale, bounds.depth * scale)

    fun toMapX(worldX: Float): Float = offsetX + (worldX - bounds.minX) * scale
    fun toMapY(worldZ: Float): Float = offsetY + (worldZ - bounds.minZ) * scale

    fun toWorldX(mapX: Float): Float = if (scale == 0f) bounds.minX else bounds.minX + (mapX - offsetX) / scale
    fun toWorldZ(mapY: Float): Float = if (scale == 0f) bounds.minZ else bounds.minZ + (mapY - offsetY) / scale

    /** A world rectangle centred on ([x], [z]) with full sizes [width] x [depth], on the map. */
    fun toMapRect(x: Float, z: Float, width: Float, depth: Float): UiRectF {
        val w = width * scale
        val h = depth * scale
        return UiRectF(toMapX(x) - w / 2f, toMapY(z) - h / 2f, w, h)
    }

    /** [rect] cut to the shown [area]; null when nothing of it is visible. */
    fun clip(rect: UiRectF): UiRectF? {
        val a = area
        val left = max(rect.x, a.x)
        val top = max(rect.y, a.y)
        val right = min(rect.right, a.right)
        val bottom = min(rect.bottom, a.bottom)
        return if (right <= left || bottom <= top) null else UiRectF(left, top, right - left, bottom - top)
    }

    fun contains(mapX: Float, mapY: Float): Boolean {
        val a = area
        return mapX >= a.x && mapX <= a.right && mapY >= a.y && mapY <= a.bottom
    }
}

/**
 * A map widget (GUI-13) on a UI element: its [UIMapMarkerComponent] children are placed from world positions every
 * frame. With [follow], it is a minimap: a [viewSize]-metre square centred on the target. Otherwise it shows the world
 * rectangle [minX]..[maxX] x [minZ]..[maxZ].
 *
 * The target ("the player") is the object named [target], else the first object with a [PlayerController].
 * A full-screen map lives on its own (usually modal, hidden) canvas: [openButton] names a button anywhere in the scene
 * that shows that canvas, [closeButton] a button that hides it; cancel (Esc / gamepad B) hides it too.
 */
@Serializable
class UIMapComponent(
    var minX: Float = -50f,
    var minZ: Float = -50f,
    var maxX: Float = 50f,
    var maxZ: Float = 50f,
    var follow: Boolean = false,
    var viewSize: Float = 30f,
    var target: String = "",
    var openButton: String = "",
    var closeButton: String = "",
) : Component() {
    fun bounds(targetX: Float, targetZ: Float): MapBounds =
        if (follow) MapBounds.around(targetX, targetZ, viewSize) else MapBounds(minX, minZ, maxX, maxZ)
}

/**
 * A marker on the parent [UIMapComponent] (GUI-13): a point of interest at ([worldX], [worldZ]), or, with
 * [tracksTarget], the target's live position. With a [worldWidth] and [worldDepth], the marker covers that world area
 * (an area pad); otherwise it keeps its own UI size. Markers outside the shown area are hidden; areas are cut to it.
 */
@Serializable
class UIMapMarkerComponent(
    var worldX: Float = 0f,
    var worldZ: Float = 0f,
    var worldWidth: Float = 0f,
    var worldDepth: Float = 0f,
    var tracksTarget: Boolean = false,
) : Component()

/** Places map markers before layout and wires full-screen map open/close buttons (GUI-13). */
class UiMapSystem : System(priority = ExecutionPriority.LATE) {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsBefore get() = listOf(UiLayoutSystem::class)
    override val runsWhilePaused get() = true

    private val maps = query(UIMapComponent::class)
    private val position = Vector3f()

    override fun update(dt: Float) {
        maps.entities.forEach { go -> updateMap(go, go.getComponent<UIMapComponent>() ?: return@forEach) }
    }

    private fun updateMap(go: GameObject, map: UIMapComponent) {
        wireButtons(go, map)
        val element = go.getComponent<UIElementComponent>() ?: return
        // Last layout's size (one frame behind on a resize), else the authored size.
        val w = element.rect.w.takeIf { it > 0f } ?: element.width
        val h = element.rect.h.takeIf { it > 0f } ?: element.height
        val target = targetPosition(map)
        val transform = MapTransform(map.bounds(target?.x ?: 0f, target?.z ?: 0f), w, h)
        go.children.forEach { child ->
            val marker = child.getComponent<UIMapMarkerComponent>() ?: return@forEach
            val e = child.getComponent<UIElementComponent>() ?: return@forEach
            place(e, marker, transform, target)
        }
    }

    private fun place(e: UIElementComponent, marker: UIMapMarkerComponent, t: MapTransform, target: Vector3f?) {
        e.anchors(0f, 0f, 0f, 0f)
        if (marker.tracksTarget) {
            if (target == null) {
                e.visible = false
                return
            }
            marker.worldX = target.x
            marker.worldZ = target.z
        }
        if (marker.worldWidth > 0f && marker.worldDepth > 0f) {
            val rect = t.clip(t.toMapRect(marker.worldX, marker.worldZ, marker.worldWidth, marker.worldDepth))
            e.visible = rect != null
            if (rect == null) return
            e.pivotX = 0f; e.pivotY = 0f
            e.x = rect.x; e.y = rect.y; e.width = rect.w; e.height = rect.h
        } else {
            val x = t.toMapX(marker.worldX)
            val y = t.toMapY(marker.worldZ)
            e.visible = t.contains(x, y)
            e.pivotX = 0.5f; e.pivotY = 0.5f
            e.x = x; e.y = y
        }
    }

    private fun targetPosition(map: UIMapComponent): Vector3f? {
        val go = (if (map.target.isNotEmpty()) find(scene) { it.name == map.target } else null)
            ?: find(scene) { it.getComponent<PlayerController>() != null }
            ?: return null
        val t = go.getComponent<Transform>() ?: return null
        // Top-level objects: the local position is the world one (and is current before the transform pass runs).
        return if (go.parent == null || go.parent === scene) position.set(t.translation) else t.worldMatrix.getTranslation(position)
    }

    private fun find(root: GameObject, match: (GameObject) -> Boolean): GameObject? {
        for (c in root.children) {
            if (match(c)) return c
            find(c, match)?.let { return it }
        }
        return null
    }

    private fun wireButtons(go: GameObject, map: UIMapComponent) {
        if (map.openButton.isEmpty() && map.closeButton.isEmpty()) return
        val canvasGo = canvasOf(go) ?: return
        val canvas = canvasGo.getComponent<UICanvasComponent>() ?: return
        if (canvas.onCancel == null) canvas.onCancel = { canvas.visible = false }
        button(map.openButton)?.let { if (it.onClick == null) it.onClick = { canvas.visible = true } }
        button(map.closeButton)?.let { if (it.onClick == null) it.onClick = { canvas.visible = false } }
    }

    private fun button(name: String): UIButtonComponent? =
        if (name.isEmpty()) null else find(scene) { it.name == name }?.getComponent<UIButtonComponent>()

    private fun canvasOf(go: GameObject): GameObject? {
        var cur: GameObject? = go
        while (cur != null) {
            if (cur.getComponent<UICanvasComponent>() != null) return cur
            cur = cur.parent
        }
        return null
    }
}
