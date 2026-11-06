package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GUI-13: the world <-> map transform, and the map system placing markers from live world positions. */
class UiMapTest {

    private val eps = 1e-4f

    @Test
    fun Transform_MapsCornersAndCentre_WithNorthUp() {
        val t = MapTransform(MapBounds(-30f, -20f, 30f, 20f), 600f, 400f)
        assertEquals(10f, t.scale, eps)
        assertEquals(0f, t.toMapX(-30f), eps)
        assertEquals(600f, t.toMapX(30f), eps)
        // -Z (north) is the top of the map, +Z the bottom.
        assertEquals(0f, t.toMapY(-20f), eps)
        assertEquals(400f, t.toMapY(20f), eps)
        assertEquals(300f, t.toMapX(0f), eps)
        assertEquals(200f, t.toMapY(0f), eps)
    }

    @Test
    fun Transform_KeepsTheAspect_AndCentresInTheMap() {
        // 60 x 40 m in a 300 x 300 square: 5 units/m, 100-unit bands above and below.
        val t = MapTransform(MapBounds(-30f, -20f, 30f, 20f), 300f, 300f)
        assertEquals(5f, t.scale, eps)
        assertEquals(0f, t.offsetX, eps)
        assertEquals(50f, t.offsetY, eps)
        assertEquals(UiRectF(0f, 50f, 300f, 200f), t.area)
        assertTrue(t.contains(150f, 150f))
        assertFalse(t.contains(150f, 20f), "the band above the world is not part of the map")
    }

    @Test
    fun Transform_WorldToMapAndBack_RoundTrips() {
        val t = MapTransform(MapBounds(-32f, -22f, 32f, 22f), 1500f, 820f)
        listOf(Vector3f(-31f, 0f, 21f), Vector3f(0f, 0f, 0f), Vector3f(12.5f, 0f, -7.25f)).forEach { p ->
            assertEquals(p.x, t.toWorldX(t.toMapX(p.x)), 1e-3f)
            assertEquals(p.z, t.toWorldZ(t.toMapY(p.z)), 1e-3f)
        }
    }

    @Test
    fun Transform_AreasAreSizedAndClipped_ToTheShownPart() {
        val t = MapTransform(MapBounds.around(0f, 0f, 20f), 200f, 200f)
        assertEquals(UiRectF(80f, 90f, 40f, 20f), t.toMapRect(0f, 0f, 4f, 2f))
        // A 10 m pad centred on the right edge: only its left half shows.
        assertEquals(UiRectF(150f, 50f, 50f, 100f), t.clip(t.toMapRect(10f, 0f, 10f, 10f)))
        assertNull(t.clip(t.toMapRect(40f, 0f, 4f, 4f)))
    }

    @Test
    fun Transform_EmptyBounds_DoNotDivideByZero() {
        val t = MapTransform(MapBounds(1f, 1f, 1f, 1f), 100f, 100f)
        assertEquals(0f, t.scale)
        assertEquals(1f, t.toWorldX(50f))
    }

    // --- UiMapSystem ---------------------------------------------------------------------------------------------

    private val manager = SystemManager()
    private val scene = Scene("Level")
    private val player = GameObject("Hero").apply { addComponent(Transform(Vector3f(5f, 0f, -5f))) }

    private fun marker(name: String, x: Float, z: Float, w: Float = 0f, d: Float = 0f, tracks: Boolean = false) =
        GameObject(name).apply {
            addComponent(UIElementComponent(width = 10f, height = 10f))
            addComponent(UIMapMarkerComponent(x, z, w, d, tracks))
        }

    private fun mapCanvas(map: UIMapComponent, vararg markers: GameObject): GameObject {
        val mapGo = GameObject("Map").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 0f, 200f, 200f))
            addComponent(map)
            markers.forEach { addChild(it) }
        }
        return GameObject("Canvas").apply {
            addComponent(UICanvasComponent(referenceWidth = 1000f, referenceHeight = 1000f))
            addChild(mapGo)
        }
    }

    private fun run(canvas: GameObject) {
        scene.addChild(player)
        scene.addChild(canvas)
        listOf(UiMapSystem(), UiLayoutSystem(screen = { UiScreenSize(1000, 1000) })).forEach { manager.addSystem(it) }
        manager.start()
        manager.loadScene(scene)
        scene.isRunning = true
        repeat(2) { manager.update(1f / 60f) }
    }

    private fun GameObject.rect() = getComponent<UIElementComponent>()!!.rect
    private fun GameObject.shown() = getComponent<UIElementComponent>()!!.visible

    @Test
    fun Minimap_KeepsThePlayerInTheCentre_AndShowsOnlyWhatIsAround() {
        val you = marker("You", 0f, 0f, tracks = true)
        val near = marker("Near", 9f, -5f)
        val far = marker("Far", 40f, 0f)
        val pad = marker("Pad", 15f, -5f, w = 10f, d = 4f)
        run(mapCanvas(UIMapComponent(follow = true, viewSize = 20f, target = "Hero"), you, near, far, pad))

        assertEquals(100f, you.rect().centerX, 0.01f)
        assertEquals(100f, you.rect().centerY, 0.01f)
        // 10 units per metre: 4 m east of the player.
        assertEquals(140f, near.rect().centerX, 0.01f)
        assertFalse(far.shown(), "a marker outside the view is hidden")
        // The pad spans x 10..20 m, the view ends at 15 m: half of it shows, cut at the edge.
        assertTrue(pad.shown())
        assertEquals(UiRectF(150f, 80f, 50f, 40f), pad.rect())
    }

    @Test
    fun FullMap_FollowsThePlayerLive() {
        val you = marker("You", 0f, 0f, tracks = true)
        run(mapCanvas(UIMapComponent(-10f, -10f, 10f, 10f, target = "Hero"), you))
        assertEquals(150f, you.rect().centerX, 0.01f)
        assertEquals(50f, you.rect().centerY, 0.01f)

        player.getComponent<Transform>()!!.translation.set(-10f, 0f, 10f)
        manager.update(1f / 60f)
        assertEquals(0f, you.rect().centerX, 0.01f)
        assertEquals(200f, you.rect().centerY, 0.01f)
    }

    @Test
    fun FullMap_OpenAndCloseButtons_AndCancelHideItsCanvas() {
        val open = GameObject("Open").apply { addComponent(UIElementComponent()); addComponent(UIButtonComponent("x")) }
        scene.addChild(open)
        val canvas = mapCanvas(UIMapComponent(target = "Hero", openButton = "Open", closeButton = "Close"))
        val close = GameObject("Close").apply { addComponent(UIElementComponent()); addComponent(UIButtonComponent("y")) }
        canvas.addChild(close)
        val c = canvas.getComponent<UICanvasComponent>()!!
        c.visible = false
        run(canvas)

        open.getComponent<UIButtonComponent>()!!.onClick!!()
        assertTrue(c.visible)
        close.getComponent<UIButtonComponent>()!!.onClick!!()
        assertFalse(c.visible)
        open.getComponent<UIButtonComponent>()!!.onClick!!()
        c.onCancel!!()
        assertFalse(c.visible)
    }

    @Test
    fun NoTarget_HidesThePlayerMarker() {
        val you = marker("You", 0f, 0f, tracks = true)
        run(mapCanvas(UIMapComponent(target = "Nobody"), you))
        assertFalse(you.shown())
    }
}
