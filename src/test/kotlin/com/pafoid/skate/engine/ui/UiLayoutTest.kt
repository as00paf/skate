package com.pafoid.skate.engine.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiLayoutTest {

    private fun assertRect(expected: UiRectF, actual: UiRectF?) {
        assertEquals(expected.x, actual!!.x, 0.001f, "x")
        assertEquals(expected.y, actual.y, 0.001f, "y")
        assertEquals(expected.w, actual.w, 0.001f, "w")
        assertEquals(expected.h, actual.h, 0.001f, "h")
    }

    private fun resolve(vararg children: UiNodeSpec, w: Float = 1000f, h: Float = 500f) =
        UiLayout.resolve(UiNodeSpec(0, children = children.toList()), w, h)

    @Test
    fun UiLayout_TopLeftAnchorOffsetsFromCorner() {
        val r = resolve(UiNodeSpec(1, x = 10f, y = 20f, width = 100f, height = 50f))
        assertRect(UiRectF(10f, 20f, 100f, 50f), r[1])
    }

    @Test
    fun UiLayout_BottomRightAnchorWithPivotPinsToCorner() {
        val r = resolve(
            UiNodeSpec(1, 1f, 1f, 1f, 1f, pivotX = 1f, pivotY = 1f, x = -10f, y = -10f, width = 100f, height = 50f)
        )
        assertRect(UiRectF(890f, 440f, 100f, 50f), r[1])
    }

    @Test
    fun UiLayout_CenterAnchorCentersElement() {
        val r = resolve(UiNodeSpec(1, .5f, .5f, .5f, .5f, .5f, .5f, width = 200f, height = 100f))
        assertRect(UiRectF(400f, 200f, 200f, 100f), r[1])
    }

    @Test
    fun UiLayout_StretchedAnchorsInsetByNegativeSize() {
        val r = resolve(UiNodeSpec(1, 0f, 0f, 1f, 1f, .5f, .5f, width = -40f, height = -20f))
        assertRect(UiRectF(20f, 10f, 960f, 480f), r[1])
    }

    @Test
    fun UiLayout_NestedChildrenAreRelativeToParentRect() {
        val child = UiNodeSpec(2, 1f, 1f, 1f, 1f, 1f, 1f, width = 10f, height = 10f)
        val parent = UiNodeSpec(1, x = 100f, y = 100f, width = 300f, height = 200f, children = listOf(child))
        val r = resolve(parent)
        assertRect(UiRectF(390f, 290f, 10f, 10f), r[2])
    }

    @Test
    fun UiLayout_VerticalGroupStacksWithSpacingAndPadding() {
        val group = UiLayoutGroup(UiLayoutKind.VERTICAL, paddingLeft = 10f, paddingTop = 10f, paddingRight = 10f, spacing = 5f)
        val a = UiNodeSpec(2, width = 50f, height = 20f)
        val b = UiNodeSpec(3, width = 50f, height = 30f)
        val r = resolve(UiNodeSpec(1, width = 200f, height = 200f, layout = group, children = listOf(a, b)))
        assertRect(UiRectF(10f, 10f, 50f, 20f), r[2])
        assertRect(UiRectF(10f, 35f, 50f, 30f), r[3])
    }

    @Test
    fun UiLayout_VerticalGroupFillCrossStretchesWidthAndCentersMainAxis() {
        val group = UiLayoutGroup(UiLayoutKind.VERTICAL, alignY = 0.5f, fillCross = true)
        val a = UiNodeSpec(2, width = 10f, height = 40f)
        val r = resolve(UiNodeSpec(1, width = 200f, height = 100f, layout = group, children = listOf(a)))
        assertRect(UiRectF(0f, 30f, 200f, 40f), r[2])
    }

    @Test
    fun UiLayout_HorizontalGroupAlignsToEnd() {
        val group = UiLayoutGroup(UiLayoutKind.HORIZONTAL, alignX = 1f, alignY = 1f, spacing = 10f)
        val a = UiNodeSpec(2, width = 30f, height = 20f)
        val b = UiNodeSpec(3, width = 30f, height = 40f)
        val r = resolve(UiNodeSpec(1, width = 200f, height = 100f, layout = group, children = listOf(a, b)))
        assertRect(UiRectF(130f, 80f, 30f, 20f), r[2])
        assertRect(UiRectF(170f, 60f, 30f, 40f), r[3])
    }

    @Test
    fun UiLayout_GridWrapsByColumns() {
        val group = UiLayoutGroup(UiLayoutKind.GRID, spacing = 4f, columns = 2, cellWidth = 20f, cellHeight = 10f)
        val kids = (2..4).map { UiNodeSpec(it) }
        val r = resolve(UiNodeSpec(1, width = 100f, height = 100f, layout = group, children = kids))
        assertRect(UiRectF(0f, 0f, 20f, 10f), r[2])
        assertRect(UiRectF(24f, 0f, 20f, 10f), r[3])
        assertRect(UiRectF(0f, 14f, 20f, 10f), r[4])
    }

    @Test
    fun UiCanvasScaler_ScaleWithScreenMatchesWidthHeightOrBlend() {
        fun s(match: Float, w: Float, h: Float) =
            UiCanvasScaler.scale(UiScaleMode.SCALE_WITH_SCREEN, 1920f, 1080f, w, h, match)
        assertEquals(2f, s(0f, 3840f, 1080f), 0.001f)
        assertEquals(1f, s(1f, 3840f, 1080f), 0.001f)
        assertEquals(2f, s(0.5f, 3840f, 2160f), 0.001f)
        assertEquals(1f, s(0.5f, 3840f, 540f), 0.001f)
    }

    @Test
    fun UiCanvasScaler_ConstantModesIgnoreScreenSize() {
        assertEquals(1f, UiCanvasScaler.scale(UiScaleMode.CONSTANT_PIXEL, 1920f, 1080f, 800f, 600f, dpiScale = 2f))
        assertEquals(1.5f, UiCanvasScaler.scale(UiScaleMode.CONSTANT_PHYSICAL, 1920f, 1080f, 800f, 600f, dpiScale = 1.5f))
    }
}
