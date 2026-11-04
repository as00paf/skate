package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputReader
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** Hover/press state of buttons and the pointer mapping that feeds it. */
class UiHoverPressTest {

    private val screen = UiScreenSize(1000, 500)
    private val layout = UiLayoutSystem(screen = { screen })
    private val input = UiInputSystem { UiInputFrame() }

    /** Canvas + button on one object, as built by hand in GUI-05: a 300x80 button centered at (350, 210). */
    private fun handBuilt(mode: UiScaleMode = UiScaleMode.CONSTANT_PIXEL): Pair<GameObject, UIButtonComponent> {
        val go = GameObject("UI")
        val button = UIButtonComponent()
        go.addComponent(button)
        UiRequirements.ensure(go, button)
        go.getComponent<UICanvasComponent>()!!.apply { scaleMode = mode; referenceWidth = 500f; referenceHeight = 250f }
        layout.layoutCanvas(go, screen)
        return go to button
    }

    private fun move(x: Float, y: Float, down: Boolean = false) = UiInputFrame(x, y, pointerDown = down)
    private fun press(x: Float, y: Float) = UiInputFrame(x, y, pointerDown = true, pointerPressed = true)
    private fun release(x: Float, y: Float) = UiInputFrame(x, y, pointerReleased = true)

    private fun visual(go: GameObject, b: UIButtonComponent) =
        UiButtonVisual.of(true, b.hovered, b.pressed, go.getComponent<UIElementComponent>()!!.focused)

    @Test
    fun Hover_EntersAndLeaves_WithoutStickyHighlight() {
        val (go, b) = handBuilt()
        val all = listOf(go)
        input.process(all, move(10f, 10f))
        assertEquals(UiButtonVisual.NORMAL, visual(go, b))

        input.process(all, move(500f, 250f))
        assertTrue(b.hovered)
        assertTrue(input.consumedInput)
        assertEquals(UiButtonVisual.HOVER, visual(go, b))

        input.process(all, move(10f, 10f))
        assertFalse(b.hovered)
        assertFalse(go.getComponent<UIElementComponent>()!!.focused, "mouse focus must not keep the hover colour")
        assertEquals(UiButtonVisual.NORMAL, visual(go, b))
    }

    @Test
    fun Press_ShowsPressedOnlyWhileOver_AndClicksOnReleaseInside() {
        val (go, b) = handBuilt()
        val all = listOf(go)
        var clicks = 0
        b.onClick = { clicks++ }

        input.process(all, move(500f, 250f))
        input.process(all, press(500f, 250f))
        assertEquals(UiButtonVisual.PRESSED, visual(go, b))

        input.process(all, move(10f, 10f, down = true))
        assertEquals(UiButtonVisual.NORMAL, visual(go, b), "dragged off: no pressed colour")
        input.process(all, move(500f, 250f, down = true))
        assertEquals(UiButtonVisual.PRESSED, visual(go, b))

        input.process(all, release(500f, 250f))
        assertEquals(1, clicks)
        assertFalse(b.pressed)
        assertEquals(UiButtonVisual.HOVER, visual(go, b))
    }

    @Test
    fun Press_ReleasedOutside_DoesNotClick() {
        val (go, b) = handBuilt()
        var clicks = 0
        b.onClick = { clicks++ }
        input.process(listOf(go), press(500f, 250f))
        input.process(listOf(go), release(10f, 10f))
        assertEquals(0, clicks)
        assertFalse(b.pressed)
    }

    @Test
    fun Hover_UsesScaledRect() {
        // Reference 500x250 on a 1000x500 screen: scale 2, so the 300x80 button covers 700..1300 (clipped) x 420..580.
        val (go, b) = handBuilt(UiScaleMode.SCALE_WITH_SCREEN)
        assertEquals(2f, go.getComponent<UICanvasComponent>()!!.scale)
        val r = go.getComponent<UIElementComponent>()!!.rect
        input.process(listOf(go), move((r.x + 5f) * 2f, (r.y + 5f) * 2f))
        assertTrue(b.hovered)
        input.process(listOf(go), move((r.x - 5f) * 2f, (r.y + 5f) * 2f))
        assertFalse(b.hovered)
    }

    @Test
    fun KeyboardNavigation_AfterHover_ShowsFocusAgain() {
        val (go, b) = handBuilt()
        input.process(listOf(go), move(500f, 250f))
        input.process(listOf(go), move(10f, 10f))
        input.process(listOf(go), UiInputFrame(10f, 10f, next = true))
        assertTrue(go.getComponent<UIElementComponent>()!!.focused)
        assertEquals(UiButtonVisual.HOVER, visual(go, b))
    }

    @Test
    fun Reset_ClearsHoverAndPress_WhenUiInputStops() {
        val (go, b) = handBuilt()
        input.process(listOf(go), press(500f, 250f))
        assertTrue(b.hovered && b.pressed)
        input.reset(listOf(go))
        assertFalse(b.hovered)
        assertFalse(b.pressed)
        assertFalse(input.consumedInput)
    }

    @Test
    fun ButtonVisual_PriorityTable() {
        assertEquals(UiButtonVisual.DISABLED, UiButtonVisual.of(false, true, true, true))
        assertEquals(UiButtonVisual.PRESSED, UiButtonVisual.of(true, true, true, false))
        assertEquals(UiButtonVisual.NORMAL, UiButtonVisual.of(true, false, true, false))
        assertEquals(UiButtonVisual.HOVER, UiButtonVisual.of(true, false, true, true))
        assertEquals(UiButtonVisual.HOVER, UiButtonVisual.of(true, true, false, false))
        assertEquals(UiButtonVisual.NORMAL, UiButtonVisual.of(true, false, false, false))
    }

    @Test
    fun DefaultColours_AreVisiblyDifferent() {
        val b = UIButtonComponent()
        fun diff(a: org.joml.Vector4f, c: org.joml.Vector4f) = maxOf(abs(a.x - c.x), abs(a.y - c.y), abs(a.z - c.z))
        assertTrue(diff(b.normalColor, b.hoverColor) >= 0.2f)
        assertTrue(diff(b.hoverColor, b.pressedColor) >= 0.2f)
        assertTrue(diff(b.normalColor, b.pressedColor) >= 0.1f)
    }

    @Test
    fun Mapping_EditorViewport_OffsetAndDpi() {
        // Viewport image at desktop (100, 50), 800x450 logical, on a 2x monitor: framebuffer 1600x900.
        val (x, y) = UiPointerMapping.toTarget(500f, 275f, 100f, 50f, 800f, 450f, 1600, 900)
        assertEquals(800f, x)
        assertEquals(450f, y)
        val (ox, oy) = UiPointerMapping.toTarget(100f, 50f, 100f, 50f, 800f, 450f, 1600, 900)
        assertEquals(0f, ox)
        assertEquals(0f, oy)
    }

    @Test
    fun Mapping_GameWindow_StretchedBlitAndIdentity() {
        assertEquals(960f to 540f, UiPointerMapping.toTarget(1280f, 720f, 0f, 0f, 2560f, 1440f, 1920, 1080))
        assertEquals(10f to 20f, UiPointerMapping.toTarget(10f, 20f, 0f, 0f, 1920f, 1080f, 1920, 1080))
        assertEquals(5f to 5f, UiPointerMapping.toTarget(15f, 15f, 10f, 10f, 0f, 0f, 1920, 1080))
    }

    @Test
    fun ReaderSource_PointerEdgesComeFromThePointerSample() {
        var sample = UiPointer(1f, 2f, false)
        val source = ReaderUiInputSource(mockk<InputReader>(relaxed = true)) { sample }
        assertFalse(source.frame(0f).pointerPressed)
        sample = UiPointer(3f, 4f, true)
        val pressed = source.frame(0f)
        assertTrue(pressed.pointerPressed && pressed.pointerDown)
        assertEquals(3f, pressed.pointerX)
        assertEquals(4f, pressed.pointerY)
        assertFalse(source.frame(0f).pointerPressed)
        sample = UiPointer(3f, 4f, false)
        assertTrue(source.frame(0f).pointerReleased)
    }
}
