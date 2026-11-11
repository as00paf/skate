package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIImageComponent
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-81: a viewport click hits the top-most UI element before any 3D object. */
class UiViewportPickTest {

    /** The viewport image, in logical pixels. */
    private val imageW = 1280f
    private val imageH = 720f

    private fun layout(scene: Scene, dpi: Float) {
        val system = UiLayoutSystem(screen = { UiScreenSize(0, 0) })
        val size = UiScreenSize(Math.round(imageW * dpi), Math.round(imageH * dpi))
        UiViewportPick.canvases(scene).forEach { system.layoutCanvas(it, size) }
    }

    /** Centre of [go]'s rect in viewport-image logical pixels. */
    private fun centreOnImage(go: GameObject, dpi: Float): Pair<Float, Float> {
        val canvas = UiViewportPick.canvases(go.scene()).first { c -> generateSequence(go) { it.parent }.any { it === c } }
            .getComponent<UICanvasComponent>()!!
        val k = canvas.scale / dpi
        val r = go.getComponent<UIElementComponent>()!!.rect
        return (r.x + r.w / 2f) * k to (r.y + r.h / 2f) * k
    }

    private fun GameObject.scene(): Scene = generateSequence(this) { it.parent }.last() as Scene

    private fun element(name: String, x: Float, y: Float, w: Float, h: Float, z: Int = 0) =
        GameObject(name).addComponent(UIElementComponent(zOrder = z).placed(0f, 0f, x, y, w, h)).addComponent(UIImageComponent())

    private fun canvas(name: String, sortOrder: Int = 0, vararg children: GameObject) =
        GameObject(name).addComponent(UICanvasComponent(sortOrder = sortOrder)).also { c -> children.forEach { c.addChild(it) } }

    @Test
    fun HigherSortOrder_IsHitFirst_EvenWhenEarlierInTheHierarchy() {
        val top = element("Top", 100f, 100f, 400f, 400f)
        val bottom = element("Bottom", 100f, 100f, 400f, 400f)
        val scene = Scene("s").apply {
            addChild(canvas("Overlay", sortOrder = 10, top))
            addChild(canvas("Hud", sortOrder = 0, bottom))
        }
        layout(scene, 1f)
        val (x, y) = centreOnImage(top, 1f)
        assertSame(top, UiViewportPick.pick(UiViewportPick.canvases(scene), x, y, 1f))
        assertEquals(listOf("Overlay", "Hud"), UiViewportPick.topFirst(UiViewportPick.canvases(scene)).map { it.name })
    }

    @Test
    fun SameSortOrder_TheLaterCanvasIsOnTop() {
        val first = element("First", 0f, 0f, 500f, 500f)
        val second = element("Second", 0f, 0f, 500f, 500f)
        val scene = Scene("s").apply {
            addChild(canvas("A", 0, first))
            addChild(canvas("B", 0, second))
        }
        layout(scene, 1f)
        val (x, y) = centreOnImage(first, 1f)
        assertSame(second, UiViewportPick.pick(UiViewportPick.canvases(scene), x, y, 1f))
    }

    @Test
    fun WithinACanvas_ChildrenAndHigherZOrder_AreOnTop() {
        val panel = element("Panel", 0f, 0f, 600f, 600f)
        val child = element("Child", 50f, 50f, 100f, 100f)
        panel.addChild(child)
        val raised = element("Raised", 300f, 300f, 100f, 100f, z = 5)
        val lowered = element("Lowered", 300f, 300f, 100f, 100f, z = 0)
        val scene = Scene("s").apply { addChild(canvas("C", 0, raised, panel, lowered)) }
        layout(scene, 1f)
        val canvases = UiViewportPick.canvases(scene)
        centreOnImage(child, 1f).let { (x, y) -> assertSame(child, UiViewportPick.pick(canvases, x, y, 1f)) }
        centreOnImage(raised, 1f).let { (x, y) -> assertSame(raised, UiViewportPick.pick(canvases, x, y, 1f)) }
    }

    @Test
    fun HiddenCanvas_AndEmptyCanvasArea_FallThroughToThe3DPick() {
        val hiddenButton = element("Hidden", 0f, 0f, 1920f, 1080f)
        val hidden = canvas("Hidden", 99, hiddenButton).also { it.getComponent<UICanvasComponent>()!!.visible = false }
        val small = element("Small", 0f, 0f, 100f, 100f)
        val scene = Scene("s").apply {
            addChild(canvas("Hud", 0, small))
            addChild(hidden)
        }
        layout(scene, 1f)
        // Over the full-screen canvas but no element: the 3D scene gets the click.
        assertNull(UiViewportPick.pick(UiViewportPick.canvases(scene), imageW / 2f, imageH / 2f, 1f))
    }

    @Test
    fun EmptyFullScreenGroup_IsClickThrough_ItsDrawnChildIsNot() {
        // TLG-16 groups the showcase UI under stretched elements that draw nothing; they must not hide the 3D scene.
        val group = GameObject("Group").addComponent(UIElementComponent().stretched())
        val button = element("Button", 0f, 0f, 200f, 100f)
        group.addChild(button)
        val scene = Scene("s").apply { addChild(canvas("Hud", 0, group)) }
        layout(scene, 1f)
        val canvases = UiViewportPick.canvases(scene)
        assertFalse(UiViewportPick.catchesClicks(group))
        assertNull(UiViewportPick.pick(canvases, imageW / 2f, imageH / 2f, 1f))
        centreOnImage(button, 1f).let { (x, y) -> assertSame(button, UiViewportPick.pick(canvases, x, y, 1f)) }
    }

    @Test
    fun DisabledCanvas_IsNotPicked() {
        val button = element("Button", 0f, 0f, 1920f, 1080f)
        val c = canvas("Off", 0, button).also { it.isEnabled = false }
        val scene = Scene("s").apply { addChild(c) }
        assertTrue(UiViewportPick.canvases(scene).isEmpty())
    }

    @Test
    fun PicksOnClick_WithTheSelectTool_OrWhenAUiElementIsSelected() {
        assertTrue(UiViewportPick.picksOnClick(selectToolActive = true, uiSelected = false))
        assertTrue(UiViewportPick.picksOnClick(selectToolActive = false, uiSelected = true))
        assertFalse(UiViewportPick.picksOnClick(selectToolActive = false, uiSelected = false))
    }

    // --- The real Feature Showcase scene (the human's repro: clicking its Map button selected nothing). ---

    private val assetsManager = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } returns `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { it.loadAnimationSync(any()) } returns Animation("walking", emptyList(), 1f, "walking.fbx")
        every { it.getTexture(any()) } returns Texture()
    }
    private val engine = mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager }

    private fun showcase() = FeatureShowcase(PrefabsGenerator(engine).apply { projectAssetsDir = "P\\Assets\\" })
        .populate(Scene(FeatureShowcase.SCENE_NAME))

    @Test
    fun FeatureShowcase_MapButton_IsPicked_At100And150Percent() {
        listOf(1f, 1.5f).forEach { dpi ->
            val scene = showcase()
            layout(scene, dpi)
            val mapButton = scene.findFirstByName(FeatureShowcase.MAP_BUTTON)!!
            assertTrue(mapButton.getComponent<UIButtonComponent>() != null)
            val (x, y) = centreOnImage(mapButton, dpi)
            // Bottom-left of the image, where the human clicked.
            assertTrue(x in 0f..imageW / 2f && y in imageH / 2f..imageH, "dpi $dpi: ($x, $y)")
            assertSame(mapButton, UiViewportPick.pick(UiViewportPick.canvases(scene), x, y, dpi), "dpi $dpi")
        }
    }

    @Test
    fun FeatureShowcase_EveryButton_IsPicked_AndTheMiddleOfTheViewIsLeftTo3D() {
        val scene = showcase()
        layout(scene, 1f)
        val canvases = UiViewportPick.canvases(scene)
        val buttons = canvases.flatMap { c -> generateSequence(listOf(c)) { l -> l.flatMap { it.children }.ifEmpty { null } }.flatten() }
            .filter { it.getComponent<UIButtonComponent>() != null && it.parent?.getComponent<UICanvasComponent>()?.visible != false }
        assertTrue(buttons.size > 5, buttons.map { it.name }.toString())
        buttons.forEach { b ->
            val (x, y) = centreOnImage(b, 1f)
            assertSame(b, UiViewportPick.pick(canvases, x, y, 1f), b.name)
        }
        // The hidden full map (a full-screen modal canvas) must not swallow clicks on the 3D scene.
        assertNull(UiViewportPick.pick(canvases, imageW / 2f, imageH / 2f, 1f))
    }

    @Test
    fun FeatureShowcase_OpenFullMap_CoversTheHud() {
        val scene = showcase()
        val map = scene.findFirstByName(FeatureShowcase.MAP_CANVAS)!!
        map.getComponent<UICanvasComponent>()!!.visible = true
        layout(scene, 1f)
        val (x, y) = centreOnImage(scene.findFirstByName(FeatureShowcase.MAP_BUTTON)!!, 1f)
        val hit = UiViewportPick.pick(UiViewportPick.canvases(scene), x, y, 1f)
        assertTrue(hit != null && generateSequence(hit) { it.parent }.any { it === map }, hit?.name)
    }
}
