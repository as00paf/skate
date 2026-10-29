package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiEditingTest {
    private fun centered(): UIElementComponent =
        UIElementComponent().placed(0.5f, 0.5f, 0f, 0f, 100f, 50f).also { it.rect = UiRectF(450f, 475f, 100f, 50f) }

    @Test
    fun UiEditing_MoveShiftsOffset() {
        val e = centered()
        UiEditing.move(e, 10f, -5f)
        assertEquals(10f, e.x)
        assertEquals(-5f, e.y)
    }

    @Test
    fun UiEditing_ResizeEastKeepsWestEdgeFixedForCenterPivot() {
        val e = centered()
        UiEditing.resize(e, UiHandle.E, 20f, 0f)
        assertEquals(120f, e.width)
        assertEquals(10f, e.x) // pivot 0.5: center moves half the delta, west edge stays
    }

    @Test
    fun UiEditing_ResizeNorthWestKeepsOppositeCornerForTopLeftPivot() {
        val e = UIElementComponent().placed(0f, 0f, 10f, 10f, 100f, 50f).also { it.rect = UiRectF(10f, 10f, 100f, 50f) }
        UiEditing.resize(e, UiHandle.NW, -10f, -10f)
        assertEquals(110f, e.width)
        assertEquals(60f, e.height)
        assertEquals(0f, e.x)
        assertEquals(0f, e.y)
    }

    @Test
    fun UiEditing_ResizeRefusesToCollapseBelowMinimum() {
        val e = centered()
        UiEditing.resize(e, UiHandle.W, 100f, 0f)
        assertEquals(100f, e.width)
    }

    @Test
    fun UiEditing_HitHandleDistinguishesCornersEdgesInteriorAndOutside() {
        val r = UiRectF(0f, 0f, 100f, 100f)
        assertEquals(UiHandle.NW, UiEditing.hitHandle(r, 1f, 2f, 4f))
        assertEquals(UiHandle.E, UiEditing.hitHandle(r, 100f, 50f, 4f))
        assertEquals(UiHandle.MOVE, UiEditing.hitHandle(r, 50f, 50f, 4f))
        assertNull(UiEditing.hitHandle(r, 200f, 50f, 4f))
    }

    @Test
    fun UiEditing_PickReturnsTopmostElement() {
        val canvas = UiFactory.create(UiKind.CANVAS)
        val below = UiFactory.create(UiKind.PANEL).also { canvas.addChild(it) }
        val above = UiFactory.create(UiKind.BUTTON).also { canvas.addChild(it) }
        below.getComponent<UIElementComponent>()!!.rect = UiRectF(0f, 0f, 100f, 100f)
        above.getComponent<UIElementComponent>()!!.rect = UiRectF(50f, 50f, 100f, 100f)
        assertEquals(above, UiEditing.pick(canvas, 60f, 60f))
        assertEquals(below, UiEditing.pick(canvas, 10f, 10f))
        assertNull(UiEditing.pick(canvas, 500f, 500f))
        assertEquals(canvas, UiEditing.canvasOf(above))
    }

    @Test
    fun UiAnchorPreset_StretchFillsParentAndPointPresetsKeepSize() {
        val e = centered()
        UiAnchorPreset.TOP_RIGHT.apply(e)
        assertEquals(1f, e.anchorMinX)
        assertEquals(0f, e.anchorMinY)
        assertEquals(1f, e.pivotX)
        assertEquals(100f, e.width)
        UiAnchorPreset.STRETCH.apply(e)
        assertEquals(1f, e.anchorMaxY)
        assertEquals(0f, e.width + 0f)
    }

    @Test
    fun UiFactory_EveryKindBuildsAndOnlyCanvasHasNoElement() {
        UiKind.entries.forEach { kind ->
            val go = UiFactory.create(kind)
            assertTrue(UiFactory.isUi(go), kind.name)
            assertEquals(kind != UiKind.CANVAS, go.getComponent<UIElementComponent>() != null, kind.name)
        }
        assertEquals(UiFactory.DEFAULT_TEXT_KEY, UiFactory.create(UiKind.TEXT).getComponent<UITextComponent>()!!.key)
    }

    @Test
    fun UiFactory_BuiltTreeSurvivesSceneJsonRoundTrip() {
        val serializer = Serializer()
        val canvas = UiFactory.create(UiKind.CANVAS)
        canvas.addChild(UiFactory.create(UiKind.LIST))
        canvas.addChild(UiFactory.create(UiKind.SLIDER))
        val decoded = serializer.decode<GameObject>(serializer.encode(canvas))
        assertEquals(2, decoded.children.size)
        assertEquals(2, decoded.children[0].getComponent<UIListComponent>()!!.items.size)
        assertEquals(0.5f, decoded.children[1].getComponent<UISliderComponent>()!!.value)
    }

    @Test
    fun ComponentType_UiTypesInstantiateAndResolveBack() {
        ComponentType.entries.filter { it.name.startsWith("UI_") }.forEach { t ->
            val c = assertNotNull(t.instantiate(), t.name).let { t.instantiate()!! }
            assertEquals(t, c.type)
        }
    }
}
