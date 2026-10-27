package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.ui.GridFont
import com.pafoid.skate.engine.render.ui.UiDrawList
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UiSystemsTest {

    private val screen = UiScreenSize(1000, 500)
    private val layout = UiLayoutSystem(screen = { screen })
    private lateinit var input: UiInputSystem

    @BeforeEach
    fun setup() {
        input = UiInputSystem { UiInputFrame() }
    }

    private fun canvas(name: String = "Canvas", sortOrder: Int = 0, modal: Boolean = false) = GameObject(name).apply {
        addComponent(UICanvasComponent(sortOrder = sortOrder, scaleMode = UiScaleMode.CONSTANT_PIXEL, modal = modal))
    }

    private fun GameObject.button(name: String, x: Float, y: Float, w: Float = 200f, h: Float = 100f, onClick: () -> Unit = {}): GameObject {
        val go = GameObject(name).apply {
            addComponent(UIElementComponent().placed(0f, 0f, x, y, w, h))
            addComponent(UIButtonComponent().also { it.onClick = onClick })
        }
        addChild(go)
        return go
    }

    private fun GameObject.slider(x: Float, y: Float, w: Float, h: Float): UISliderComponent {
        val s = UISliderComponent(0f, 1f, 0f, 0.25f)
        addChild(GameObject("slider").apply {
            addComponent(UIElementComponent().placed(0f, 0f, x, y, w, h))
            addComponent(s)
        })
        return s
    }

    private fun press(x: Float, y: Float) = UiInputFrame(x, y, pointerDown = true, pointerPressed = true)
    private fun release(x: Float, y: Float) = UiInputFrame(x, y, pointerReleased = true)
    private fun nav(vararg d: UiDirection, confirm: Boolean = false) = UiInputFrame(navigate = d.toSet(), confirm = confirm)

    @Test
    fun UiLayoutSystem_ResolvesRectsAndScalesWithScreen() {
        val c = GameObject("C").apply {
            addComponent(UICanvasComponent(scaleMode = UiScaleMode.SCALE_WITH_SCREEN, referenceWidth = 500f, referenceHeight = 250f))
        }
        val b = c.button("b", 10f, 20f, 100f, 50f)
        layout.layoutCanvas(c, screen)
        val canvas = c.getComponent<UICanvasComponent>()!!
        assertEquals(2f, canvas.scale, 0.001f)
        assertEquals(500f, canvas.width, 0.001f)
        assertEquals(UiRectF(10f, 20f, 100f, 50f), b.getComponent<UIElementComponent>()!!.rect)
    }

    @Test
    fun UiLayoutSystem_RelayoutsOnlyWhenLayoutInputsChange() {
        val c = canvas()
        val b = c.button("b", 10f, 10f)
        val e = b.getComponent<UIElementComponent>()!!
        layout.layoutCanvas(c, screen)
        e.rect = UiRectF(-1f, -1f, -1f, -1f)
        layout.layoutCanvas(c, screen)
        assertEquals(-1f, e.rect.x, "unchanged tree is not re-resolved")
        e.x = 50f
        layout.layoutCanvas(c, screen)
        assertEquals(50f, e.rect.x)
    }

    @Test
    fun UiInput_ClickFiresOnReleaseOverSameButton() {
        var clicks = 0
        val c = canvas()
        val b = c.button("b", 100f, 100f) { clicks++ }
        layout.layoutCanvas(c, screen)
        val btn = b.getComponent<UIButtonComponent>()!!

        input.process(listOf(c), press(150f, 150f))
        assertTrue(btn.pressed)
        assertEquals(0, clicks)
        input.process(listOf(c), release(150f, 150f))
        assertFalse(btn.pressed)
        assertEquals(1, clicks)
    }

    @Test
    fun UiInput_ReleaseOutsideCancelsClick() {
        var clicks = 0
        val c = canvas()
        c.button("b", 100f, 100f) { clicks++ }
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), press(150f, 150f))
        input.process(listOf(c), release(600f, 400f))
        assertEquals(0, clicks)
    }

    @Test
    fun UiInput_HoverSetsStateAndConsumesInput() {
        val c = canvas()
        val btn = c.button("b", 100f, 100f).getComponent<UIButtonComponent>()!!
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), UiInputFrame(150f, 150f))
        assertTrue(btn.hovered)
        assertTrue(input.consumedInput)
        input.process(listOf(c), UiInputFrame(900f, 400f))
        assertFalse(btn.hovered)
        assertFalse(input.consumedInput)
    }

    @Test
    fun UiInput_NonInteractableButtonIgnoresClicks() {
        var clicks = 0
        val c = canvas()
        val b = c.button("b", 100f, 100f) { clicks++ }
        b.getComponent<UIElementComponent>()!!.interactable = false
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), press(150f, 150f))
        input.process(listOf(c), release(150f, 150f))
        assertEquals(0, clicks)
    }

    @Test
    fun UiInput_GamepadNavigationMovesFocusAndConfirmClicks() {
        val clicked = mutableListOf<String>()
        val c = canvas()
        val top = c.button("top", 100f, 50f) { clicked += "top" }
        val bottom = c.button("bottom", 100f, 250f) { clicked += "bottom" }
        layout.layoutCanvas(c, screen)

        input.process(listOf(c), nav(UiDirection.DOWN))
        assertEquals(top.uId, input.focusedId)
        input.process(listOf(c), nav(UiDirection.DOWN))
        assertEquals(bottom.uId, input.focusedId)
        assertTrue(bottom.getComponent<UIElementComponent>()!!.focused)
        input.process(listOf(c), nav(UiDirection.UP))
        assertEquals(top.uId, input.focusedId)
        input.process(listOf(c), nav(UiDirection.DOWN))
        input.process(listOf(c), nav(confirm = true))
        assertEquals(listOf("bottom"), clicked)
    }

    @Test
    fun UiInput_TabCyclesFocus() {
        val c = canvas()
        val a = c.button("a", 100f, 50f)
        val b = c.button("b", 100f, 250f)
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), UiInputFrame(next = true))
        assertEquals(a.uId, input.focusedId)
        input.process(listOf(c), UiInputFrame(next = true))
        assertEquals(b.uId, input.focusedId)
        input.process(listOf(c), UiInputFrame(previous = true))
        assertEquals(a.uId, input.focusedId)
    }

    @Test
    fun UiInput_ModalCanvasTrapsInputAndFocus() {
        var behind = 0
        var inFront = 0
        val game = canvas("game", sortOrder = 0)
        game.button("b", 100f, 100f) { behind++ }
        val menu = canvas("menu", sortOrder = 10, modal = true)
        val menuBtn = menu.button("m", 600f, 300f, 100f, 100f) { inFront++ }
        layout.layoutCanvas(game, screen)
        layout.layoutCanvas(menu, screen)

        input.process(listOf(game, menu), press(150f, 150f))
        input.process(listOf(game, menu), release(150f, 150f))
        assertEquals(0, behind)
        assertEquals(menuBtn.uId, input.focusedId, "modal auto-focuses its first widget")
        assertTrue(input.consumedInput)

        input.process(listOf(game, menu), nav(confirm = true))
        assertEquals(1, inFront)
    }

    @Test
    fun UiInput_CancelInvokesTopCanvasHandler() {
        var cancelled = 0
        val c = canvas()
        c.getComponent<UICanvasComponent>()!!.onCancel = { cancelled++ }
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), UiInputFrame(cancel = true))
        assertEquals(1, cancelled)
    }

    @Test
    fun UiInput_SliderDragSetsSnappedValueAndNotifies() {
        val c = canvas()
        val slider = c.slider(100f, 100f, 400f, 40f)
        val seen = mutableListOf<Float>()
        slider.onValueChanged = { seen += it }
        layout.layoutCanvas(c, screen)

        input.process(listOf(c), press(400f, 120f))
        assertEquals(0.75f, slider.value, 0.0001f)
        input.process(listOf(c), UiInputFrame(500f, 120f, pointerDown = true))
        assertEquals(1f, slider.value, 0.0001f)
        input.process(listOf(c), release(500f, 120f))
        assertFalse(slider.dragging)
        assertEquals(listOf(0.75f, 1f), seen)
    }

    @Test
    fun UiInput_FocusedSliderConsumesLeftRight() {
        val c = canvas()
        val slider = c.slider(100f, 100f, 400f, 40f)
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), nav(UiDirection.DOWN))
        input.process(listOf(c), nav(UiDirection.RIGHT))
        input.process(listOf(c), nav(UiDirection.RIGHT))
        input.process(listOf(c), nav(UiDirection.LEFT))
        assertEquals(0.25f, slider.value, 0.0001f)
    }

    @Test
    fun UiInput_ListNavigationSelectsRowsThenScrolls() {
        val c = canvas()
        val list = UIListComponent(items = (0 until 10).map { UiListItem("k$it") }, itemHeight = 40f)
        c.addChild(GameObject("list").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 0f, 300f, 120f))
            addComponent(list)
        })
        layout.layoutCanvas(c, screen)
        input.process(listOf(c), nav(UiDirection.DOWN))
        repeat(4) { input.process(listOf(c), nav(UiDirection.DOWN)) }
        assertEquals(4, list.selectedIndex)
        assertEquals(80f, list.scroll, 0.001f)
    }

    private fun renderFixture(): Triple<UiRenderSystem, UiDrawList, StringManager> {
        val strings = mockk<StringManager>()
        every { strings.getString("hud.title") } returns "Hi"
        every { strings.getString("hud.score", "42") } returns "Score 42"
        val font = GridFont(Texture(256, 96, 1, 4), 16, 6)
        val system = UiRenderSystem({ null }, strings, { font }, { null })
        return Triple(system, UiDrawList(), strings)
    }

    @Test
    fun UiRender_TextResolvesThroughStringManagerKeys() {
        val (system, list) = renderFixture()
        val c = canvas()
        c.addChild(GameObject("t").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 0f, 300f, 60f))
            addComponent(UITextComponent(key = "hud.score", args = listOf("42"), fontSize = 20f))
        })
        c.addChild(GameObject("t2").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 100f, 300f, 60f))
            addComponent(UITextComponent(key = "hud.title", fontSize = 20f))
        })
        layout.layoutCanvas(c, screen)
        system.draw(list, listOf(c))
        assertEquals("Score 42".length + "Hi".length, list.quadCount)
    }

    @Test
    fun UiRender_SkipsHiddenElementsAndCanvases() {
        val (system, list) = renderFixture()
        val c = canvas()
        val bar = GameObject("bar").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 0f, 100f, 20f))
            addComponent(UIProgressBarComponent(value = 0.5f))
        }
        c.addChild(bar)
        layout.layoutCanvas(c, screen)

        system.draw(list, listOf(c))
        assertEquals(2, list.quadCount)
        list.clear()
        bar.getComponent<UIElementComponent>()!!.visible = false
        system.draw(list, listOf(c))
        assertEquals(0, list.quadCount)
        bar.getComponent<UIElementComponent>()!!.visible = true
        c.getComponent<UICanvasComponent>()!!.visible = false
        system.draw(list, listOf(c))
        assertEquals(0, list.quadCount)
    }

    @Test
    fun UiRender_ProgressFillWidthFollowsValue() {
        val (system, list) = renderFixture()
        val c = canvas()
        c.addChild(GameObject("bar").apply {
            addComponent(UIElementComponent().placed(0f, 0f, 0f, 0f, 200f, 20f))
            addComponent(UIProgressBarComponent(value = 0.25f))
        })
        layout.layoutCanvas(c, screen)
        system.draw(list, listOf(c))
        // quad 1 (fill): corners (0,0) (1,0) ... -> x of the second vertex is the fill width
        val stride = com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
        assertEquals(50f, list.vertexData[4 * stride + stride], 0.001f)
    }

    @Test
    fun UiSerialization_UiComponentsRoundTripThroughSceneJson() {
        val serializer = com.pafoid.skate.engine.assets.serialization.Serializer()
        val c = canvas()
        c.addComponent(UIElementComponent().stretched(10f, 10f, 10f, 10f))
        c.button("b", 1f, 2f).getComponent<UIButtonComponent>()!!.labelKey = "pause.resume"
        val decoded = serializer.decode<GameObject>(serializer.encode(c))
        val button = decoded.children.single().getComponent<UIButtonComponent>()!!
        assertEquals("pause.resume", button.labelKey)
        assertNull(button.onClick)
        assertEquals(UiScaleMode.CONSTANT_PIXEL, decoded.getComponent<UICanvasComponent>()!!.scaleMode)
    }
}
