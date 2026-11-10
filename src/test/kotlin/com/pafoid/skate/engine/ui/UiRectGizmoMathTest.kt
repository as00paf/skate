package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-75: rect maths behind the viewport UI gizmo (move/resize with snapping, pivot and anchor editing). */
class UiRectGizmoMathTest {
    private val parent = UiRectF(100f, 50f, 800f, 400f)
    private val eps = 1e-3f

    /** The real layout resolver, so every edit is checked against what the game will draw. */
    private fun resolved(e: UIElementComponent, p: UiRectF = parent) = UiLayout.anchored(
        UiNodeSpec(1, e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY, e.pivotX, e.pivotY, e.x, e.y, e.width, e.height),
        p,
    )

    private fun assertRect(expected: UiRectF, actual: UiRectF) {
        assertEquals(expected.x, actual.x, eps, "x")
        assertEquals(expected.y, actual.y, eps, "y")
        assertEquals(expected.w, actual.w, eps, "w")
        assertEquals(expected.h, actual.h, eps, "h")
    }

    private fun element(anchors: FloatArray, pivotX: Float, pivotY: Float) = UIElementComponent(
        anchorMinX = anchors[0], anchorMinY = anchors[1], anchorMaxX = anchors[2], anchorMaxY = anchors[3],
        pivotX = pivotX, pivotY = pivotY, x = 13f, y = -7f, width = 120f, height = 60f,
    ).also { it.rect = resolved(it) }

    private val pointCenter = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
    private val stretch = floatArrayOf(0.1f, 0.2f, 0.9f, 0.7f)

    @Test
    fun setRect_IsTheInverseOfLayoutForPointAndStretchedAnchors() {
        val target = UiRectF(220f, 130f, 333f, 77f)
        for (anchors in listOf(pointCenter, stretch, floatArrayOf(0f, 1f, 0f, 1f))) {
            for (pivot in listOf(0f to 0f, 0.5f to 0.5f, 1f to 0.25f)) {
                val e = element(anchors, pivot.first, pivot.second)
                UiEditing.setRect(e, parent, target)
                assertRect(target, resolved(e))
            }
        }
    }

    @Test
    fun dragRect_MoveKeepsSizeAndSnapsTheTopLeftCorner() {
        val start = UiRectF(100f, 100f, 50f, 20f)
        assertRect(UiRectF(103f, 96f, 50f, 20f), UiEditing.dragRect(start, UiHandle.MOVE, 3f, -4f))
        assertRect(UiRectF(100f, 100f, 50f, 20f), UiEditing.dragRect(start, UiHandle.MOVE, 3f, -4f, snap = 10f))
        assertRect(UiRectF(110f, 90f, 50f, 20f), UiEditing.dragRect(start, UiHandle.MOVE, 7f, -6f, snap = 10f))
    }

    @Test
    fun dragRect_ResizeMovesOnlyTheGrabbedEdgesAndSnapsThem() {
        val start = UiRectF(100f, 100f, 50f, 20f)
        assertRect(UiRectF(100f, 100f, 57f, 20f), UiEditing.dragRect(start, UiHandle.E, 7f, 99f))
        // East edge 150 + 7 = 157 snaps to 160; the west edge stays.
        assertRect(UiRectF(100f, 100f, 60f, 20f), UiEditing.dragRect(start, UiHandle.E, 7f, 0f, snap = 10f))
        // NW corner: both west and north move; south-east corner stays at (150, 120).
        assertRect(UiRectF(90f, 80f, 60f, 40f), UiEditing.dragRect(start, UiHandle.NW, -12f, -18f, snap = 10f))
    }

    @Test
    fun dragRect_ResizeNeverCrossesTheOppositeEdge() {
        val start = UiRectF(100f, 100f, 50f, 20f)
        val r = UiEditing.dragRect(start, UiHandle.W, 500f, 0f)
        assertEquals(150f, r.right, eps)
        assertEquals(1f, r.w, eps)
        assertEquals(1f, UiEditing.dragRect(start, UiHandle.S, 0f, -500f).h, eps)
    }

    @Test
    fun snapped_RoundsToTheNearestStepAndIsOffAtZero() {
        assertEquals(20f, UiEditing.snapped(16f, 10f))
        assertEquals(10f, UiEditing.snapped(14.9f, 10f))
        assertEquals(-10f, UiEditing.snapped(-7f, 10f))
        assertEquals(16.3f, UiEditing.snapped(16.3f, 0f))
    }

    @Test
    fun setPivotKeepingRect_ChangesPivotButNotWhereTheElementIs() {
        val e = element(stretch, 0.5f, 0.5f)
        val before = e.rect
        UiEditing.setPivotKeepingRect(e, parent, before, 0f, 1.5f)
        assertEquals(0f, e.pivotX)
        assertEquals(1f, e.pivotY) // clamped
        assertRect(before, resolved(e))
    }

    @Test
    fun setAnchorKeepingRect_MovesAPointAnchorAsOneAndKeepsTheRect() {
        val e = element(pointCenter, 0.5f, 0.5f)
        val before = e.rect
        UiEditing.setAnchorKeepingRect(e, parent, before, UiHandle.ANCHOR_MIN, 1f, 0f)
        assertEquals(listOf(1f, 0f, 1f, 0f), listOf(e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY))
        assertRect(before, resolved(e))
    }

    @Test
    fun setAnchorKeepingRect_StretchedCornersMoveIndependentlyAndNeverCross() {
        val e = element(stretch, 0.5f, 0.5f)
        val before = e.rect
        UiEditing.setAnchorKeepingRect(e, parent, before, UiHandle.ANCHOR_MAX, 1f, 1f)
        assertEquals(listOf(0.1f, 0.2f, 1f, 1f), listOf(e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY))
        assertRect(before, resolved(e))

        UiEditing.setAnchorKeepingRect(e, parent, before, UiHandle.ANCHOR_MIN, 1f, 1f)
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf(e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY))
        assertRect(before, resolved(e))
    }

    @Test
    fun drag_IsAbsoluteFromTheStartSoSnappingDoesNotLoseSmallMotions() {
        val e = element(pointCenter, 0.5f, 0.5f)
        val start = UiDragStart(e.rect, parent, e.width, e.height, pointerX = 0f, pointerY = 0f, layoutManaged = false)
        // Ten 1-unit frames with a 10-unit grid: incremental snapping would never move; absolute moves once.
        for (i in 1..10) UiEditing.drag(e, start, UiHandle.MOVE, i.toFloat(), 0f, snap = 10f)
        val expectedX = UiEditing.snapped(start.rect.x + 10f, 10f)
        assertEquals(expectedX, resolved(e).x, eps)
        assertEquals(start.rect.w, resolved(e).w, eps)
    }

    @Test
    fun drag_PivotAndAnchorsSnapToFractionsWhileSnapping() {
        val e = element(pointCenter, 0.5f, 0.5f)
        val r = e.rect
        val start = UiDragStart(r, parent, e.width, e.height, 0f, 0f, false)
        UiEditing.drag(e, start, UiHandle.PIVOT, r.x + r.w * 0.26f, r.y + r.h * 0.98f, snap = 10f)
        assertEquals(0.25f, e.pivotX, eps)
        assertEquals(1f, e.pivotY, eps)
        assertRect(r, resolved(e))

        UiEditing.drag(e, start, UiHandle.ANCHOR_MIN, parent.x + parent.w * 0.02f, parent.y + parent.h * 0.51f, snap = 10f)
        assertEquals(0f, e.anchorMinX, eps)
        assertEquals(0.5f, e.anchorMinY, eps)
        assertRect(r, resolved(e))
    }

    @Test
    fun drag_LayoutManagedElementsOnlyChangeSize() {
        val e = element(pointCenter, 0.5f, 0.5f)
        val start = UiDragStart(e.rect, parent, e.width, e.height, 0f, 0f, layoutManaged = true)
        UiEditing.drag(e, start, UiHandle.MOVE, 50f, 50f)
        UiEditing.drag(e, start, UiHandle.PIVOT, 0f, 0f)
        assertEquals(listOf(13f, -7f, 0.5f, 0.5f), listOf(e.x, e.y, e.pivotX, e.pivotY))

        UiEditing.drag(e, start, UiHandle.SE, 30f, 10f)
        assertEquals(150f, e.width, eps)
        assertEquals(70f, e.height, eps)
        assertEquals(13f, e.x)
    }

    @Test
    fun hitGizmo_PrefersEdgesThenPivotThenAnchorsThenInterior() {
        val e = UIElementComponent().placed(0.5f, 0.5f, 0f, 0f, 200f, 100f).also { it.rect = UiRectF(400f, 200f, 200f, 100f) }
        val p = UiRectF(0f, 0f, 1000f, 500f)
        assertEquals(UiHandle.NW, UiEditing.hitGizmo(e, p, 401f, 201f, 4f))
        assertEquals(UiHandle.PIVOT, UiEditing.hitGizmo(e, p, 501f, 250f, 4f))
        assertEquals(UiHandle.MOVE, UiEditing.hitGizmo(e, p, 450f, 220f, 4f))
        assertEquals(UiHandle.MOVE, UiEditing.hitGizmo(e, p, 501f, 250f, 4f, editAnchors = false))
        assertNull(UiEditing.hitGizmo(e, p, 900f, 450f, 4f))

        val offset = UIElementComponent().placed(0f, 0f, 300f, 300f, 50f, 50f).also { it.rect = UiRectF(300f, 300f, 50f, 50f) }
        assertEquals(UiHandle.ANCHOR_MAX, UiEditing.hitGizmo(offset, p, 2f, 1f, 4f)) // point anchor: max wins, moves both
    }

    @Test
    fun parentRectAndLayoutManagement_FollowTheHierarchy() {
        val canvasGo = GameObject("Canvas").apply {
            addComponent(UICanvasComponent().also { it.width = 1920f; it.height = 1080f })
        }
        val panelElement = UIElementComponent().also { it.rect = UiRectF(10f, 20f, 300f, 200f) }
        val panel = GameObject("Panel").apply { addComponent(panelElement) }
        val child = GameObject("Child").apply { addComponent(UIElementComponent()) }
        canvasGo.addChild(panel)
        panel.addChild(child)

        assertEquals(UiRectF(0f, 0f, 1920f, 1080f), UiEditing.parentRect(panel))
        assertEquals(panelElement.rect, UiEditing.parentRect(child))
        assertNull(UiEditing.parentRect(GameObject("Loose")))

        assertFalse(UiEditing.isLayoutManaged(child))
        panelElement.layout = UiLayoutGroup(kind = UiLayoutKind.VERTICAL)
        assertTrue(UiEditing.isLayoutManaged(child))
        assertFalse(UiEditing.isLayoutManaged(panel))
    }
}
