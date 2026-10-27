package com.pafoid.skate.engine.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiNavigationTest {

    private fun f(id: Int, x: Float, y: Float) = UiFocusable(id, UiRectF(x, y, 100f, 40f))

    private val grid = listOf(f(1, 0f, 0f), f(2, 200f, 0f), f(3, 0f, 100f), f(4, 200f, 100f))

    @Test
    fun UiFocus_MovesToNeighbourInDirection() {
        assertEquals(2, UiFocusNavigator.navigate(grid[0], grid, UiDirection.RIGHT))
        assertEquals(3, UiFocusNavigator.navigate(grid[0], grid, UiDirection.DOWN))
        assertEquals(3, UiFocusNavigator.navigate(grid[3], grid, UiDirection.LEFT))
        assertEquals(2, UiFocusNavigator.navigate(grid[3], grid, UiDirection.UP))
    }

    @Test
    fun UiFocus_NoCandidateInDirectionReturnsNull() {
        assertNull(UiFocusNavigator.navigate(grid[0], grid, UiDirection.UP))
        assertNull(UiFocusNavigator.navigate(grid[0], grid, UiDirection.LEFT))
    }

    @Test
    fun UiFocus_PrefersSameColumnOverCloserDiagonal() {
        val list = listOf(f(1, 0f, 0f), f(2, 120f, 50f), f(3, 0f, 200f))
        assertEquals(3, UiFocusNavigator.navigate(list[0], list, UiDirection.DOWN))
        assertEquals(2, UiFocusNavigator.navigate(list[0], list, UiDirection.RIGHT))
    }

    @Test
    fun UiFocus_NoCurrentFocusStartsAtTopLeft() {
        assertEquals(1, UiFocusNavigator.navigate(null, grid.reversed(), UiDirection.DOWN))
    }

    @Test
    fun UiFocus_CycleWrapsInReadingOrder() {
        assertEquals(2, UiFocusNavigator.cycle(1, grid, true))
        assertEquals(1, UiFocusNavigator.cycle(4, grid, true))
        assertEquals(4, UiFocusNavigator.cycle(1, grid, false))
        assertEquals(1, UiFocusNavigator.cycle(null, grid, true))
    }

    @Test
    fun UiNavRepeater_FiresOnceThenRepeatsAfterDelay() {
        val r = UiNavRepeater(initialDelay = 0.4f, repeatInterval = 0.1f)
        assertTrue(r.update(true, 0.016f))
        assertFalse(r.update(true, 0.2f))
        assertFalse(r.update(true, 0.1f))
        assertTrue(r.update(true, 0.15f))
        assertFalse(r.update(true, 0.01f))
        assertFalse(r.update(false, 0.1f))
        assertTrue(r.update(true, 0.016f))
    }

    @Test
    fun UiWidgetMath_SliderSnapsToStepAndClamps() {
        assertEquals(0.5f, UiWidgetMath.valueFromFraction(0.52f, 0f, 1f, 0.25f), 0.0001f)
        assertEquals(1f, UiWidgetMath.valueFromFraction(2f, 0f, 1f, 0.25f), 0.0001f)
        assertEquals(0.3f, UiWidgetMath.valueFromFraction(0.3f, 0f, 1f, 0f), 0.0001f)
        assertEquals(0.5f, UiWidgetMath.fraction(15f, 10f, 20f), 0.0001f)
        assertEquals(0f, UiWidgetMath.fraction(5f, 10f, 10f))
    }

    @Test
    fun UiWidgetMath_ListVisibleRangeAndScrollIntoView() {
        assertEquals(0..2, UiWidgetMath.visibleItems(10, 40f, 100f, 0f))
        assertEquals(2..4, UiWidgetMath.visibleItems(10, 40f, 100f, 90f))
        assertEquals(40f, UiWidgetMath.scrollToItem(3, 10, 40f, 120f, 0f), 0.001f)
        assertEquals(0f, UiWidgetMath.scrollToItem(0, 10, 40f, 120f, 40f), 0.001f)
        assertEquals(0f, UiWidgetMath.scrollToItem(1, 2, 40f, 200f, 0f), 0.001f)
    }
}
