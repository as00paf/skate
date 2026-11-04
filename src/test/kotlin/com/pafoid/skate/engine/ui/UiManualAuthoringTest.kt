package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.ui.UiDrawList
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI built by hand: plain GameObjects plus components added one at a time, as the inspector's Add Component does. */
class UiManualAuthoringTest {

    private val screen = UiScreenSize(1000, 500)
    private val layout = UiLayoutSystem(screen = { screen })
    private val render = UiRenderSystem({ null }, mockk<StringManager>(relaxed = true), { null }, { null })

    /** Mirrors the editor's Add Component: attach, then let the requirements fill in what is missing. */
    private fun GameObject.add(c: Component): List<Component> {
        addComponent(c::class as kotlin.reflect.KClass<Component>, c)
        return UiRequirements.ensure(this, c)
    }

    private fun drawnQuads(vararg canvases: GameObject): Int {
        canvases.forEach { layout.layoutCanvas(it, screen) }
        val list = UiDrawList()
        render.draw(list, canvases.toList())
        return list.quadCount
    }

    private fun constantPixel(go: GameObject) {
        go.getComponent<UICanvasComponent>()!!.scaleMode = UiScaleMode.CONSTANT_PIXEL
    }

    @Test
    fun CanvasThenButtonOnSameObject_IsRenderedCentered() {
        val go = GameObject("UI")
        go.add(UICanvasComponent())
        val added = go.add(UIButtonComponent())
        constantPixel(go)

        assertEquals(listOf(UIElementComponent::class), added.map { it::class })
        assertEquals(1, drawnQuads(go))
        assertEquals(UiRectF(350f, 210f, 300f, 80f), go.getComponent<UIElementComponent>()!!.rect)
        assertEquals(UiFactory.DEFAULT_BUTTON_KEY, go.getComponent<UIButtonComponent>()!!.labelKey)
    }

    @Test
    fun ButtonThenCanvasOnSameObject_ButtonAloneGetsRectAndCanvas() {
        val go = GameObject("UI")
        val added = go.add(UIButtonComponent())
        constantPixel(go)

        assertEquals(setOf(UIElementComponent::class, UICanvasComponent::class), added.map { it::class }.toSet())
        assertNull(UiRequirements.problem(go))
        assertEquals(1, drawnQuads(go))
    }

    @Test
    fun ButtonOnChildOfCanvas_OnlyGetsRect() {
        val canvas = GameObject("Canvas")
        canvas.add(UICanvasComponent())
        constantPixel(canvas)
        val child = GameObject("Child")
        canvas.addChild(child)

        val added = child.add(UIButtonComponent())

        assertEquals(listOf(UIElementComponent::class), added.map { it::class })
        assertNull(canvas.getComponent<UIElementComponent>())
        assertEquals(1, drawnQuads(canvas))
        assertEquals(UiRectF(350f, 210f, 300f, 80f), child.getComponent<UIElementComponent>()!!.rect)
    }

    @Test
    fun SavedSceneWithWidgetMissingRect_IsRepairedByLayout() {
        val canvas = GameObject("Canvas").apply { addComponent(UICanvasComponent(scaleMode = UiScaleMode.CONSTANT_PIXEL)) }
        val child = GameObject("Button").apply { addComponent(UIButtonComponent(labelKey = "x")) }
        canvas.addChild(child)

        assertEquals(1, drawnQuads(canvas))
        assertNotNull(child.getComponent<UIElementComponent>())
    }

    @Test
    fun ManualCanvasAndButton_SurviveSaveAndReload() {
        val root = GameObject("Root")
        val canvas = GameObject("Canvas")
        root.addChild(canvas)
        canvas.add(UICanvasComponent())
        canvas.add(UIButtonComponent())
        constantPixel(canvas)
        canvas.getComponent<UIElementComponent>()!!.x = 40f

        val serializer = Serializer()
        val reloaded = serializer.decode<GameObject>(serializer.encode(root)).children.single()

        assertEquals(1, drawnQuads(reloaded))
        val e = reloaded.getComponent<UIElementComponent>()!!
        assertEquals(UiRectF(390f, 210f, 300f, 80f), e.rect)
        assertEquals(UiFactory.DEFAULT_BUTTON_KEY, reloaded.getComponent<UIButtonComponent>()!!.labelKey)
    }

    @Test
    fun RootWidget_IsPickableForTheRectGizmo_ButBareRootIsNot() {
        val withButton = GameObject("UI")
        withButton.add(UIButtonComponent())
        constantPixel(withButton)
        layout.layoutCanvas(withButton, screen)
        assertSame(withButton, UiEditing.pick(withButton, 500f, 250f))
        assertSame(withButton, UiEditing.canvasOf(withButton))

        val hud = GameObject("Hud").apply {
            addComponent(UICanvasComponent(scaleMode = UiScaleMode.CONSTANT_PIXEL))
            addComponent(UIElementComponent().stretched())
        }
        layout.layoutCanvas(hud, screen)
        assertNull(UiEditing.pick(hud, 500f, 250f))
    }

    @Test
    fun RootRect_ResizesThroughUiEditing() {
        val go = GameObject("UI")
        go.add(UIButtonComponent())
        constantPixel(go)
        layout.layoutCanvas(go, screen)
        val e = go.getComponent<UIElementComponent>()!!

        UiEditing.resize(e, UiHandle.E, 20f, 0f)
        layout.layoutCanvas(go, screen)

        assertEquals(320f, e.rect.w)
        assertEquals(350f, e.rect.x)
    }

    @Test
    fun NestedCanvas_IsDrawnOnceAsItsOwnTree() {
        val outer = GameObject("Outer").apply { addComponent(UICanvasComponent(scaleMode = UiScaleMode.CONSTANT_PIXEL)) }
        val inner = GameObject("Inner")
        outer.addChild(inner)
        inner.add(UICanvasComponent())
        inner.add(UIButtonComponent())
        constantPixel(inner)
        // The outer canvas does not adopt the inner one; it keeps drawing as its own tree.
        assertEquals(1, drawnQuads(outer, inner))
        assertTrue(UiTree.elements(outer).isEmpty())
    }

    @Test
    fun Problem_ReportsMissingRectThenMissingCanvas() {
        val go = GameObject("Plain").apply { addComponent(UITextComponent(key = "k")) }
        assertEquals(UiProblem.NO_ELEMENT, UiRequirements.problem(go))
        go.addComponent(UIElementComponent())
        assertEquals(UiProblem.NO_CANVAS, UiRequirements.problem(go))
        go.addComponent(UICanvasComponent())
        assertNull(UiRequirements.problem(go))
        assertNull(UiRequirements.problem(GameObject("NotUi")))
    }

    @Test
    fun FactoryCreatedUi_StillRendersUnderItsCanvas() {
        val canvas = UiFactory.create(UiKind.CANVAS).also { constantPixel(it) }
        canvas.addChild(UiFactory.create(UiKind.BUTTON))
        canvas.addChild(UiFactory.create(UiKind.PANEL))
        assertEquals(2, drawnQuads(canvas))
    }
}
