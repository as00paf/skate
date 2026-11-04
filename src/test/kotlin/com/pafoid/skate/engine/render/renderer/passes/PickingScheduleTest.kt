package com.pafoid.skate.engine.render.renderer.passes

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-22: the picking pass renders only in frames after a pick was requested. */
class PickingScheduleTest {

    @Test
    fun PickingSchedule_NoRequest_NoRender() {
        val s = PickingSchedule()
        assertFalse(s.onFrame())
        assertFalse(s.onFrame())
        assertFalse(s.fresh)
    }

    @Test
    fun PickingSchedule_RequestRendersOnceAndStaysFreshForTheNextRead() {
        val s = PickingSchedule()
        s.request()
        assertTrue(s.onFrame())
        assertTrue(s.fresh)
        assertFalse(s.onFrame(), "no new request: next frame skips")
        assertFalse(s.fresh, "a skipped frame makes the texture stale")
    }

    @Test
    fun PickingSchedule_RenderNowMakesAStaleTextureFresh() {
        val s = PickingSchedule()
        s.onFrame()
        s.onRenderedNow()
        assertTrue(s.fresh)
    }
}
