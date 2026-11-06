package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.removeComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComponentRemovalConfirmationTest {

    private val transform = Transform()
    private val canvas = UICanvasComponent()
    private val go = GameObject("Box").addComponent(transform).addComponent(canvas)

    @Test
    fun request_OpensPopupExactlyOnce() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        assertTrue(s.consumeOpenRequest())
        assertFalse(s.consumeOpenRequest(), "the popup is opened once, not every frame")
        assertSame(canvas, s.pending)
    }

    @Test
    fun noRequest_NeverOpens() {
        val s = ComponentRemovalConfirmation()
        repeat(10) {
            assertFalse(s.consumeOpenRequest())
            s.sync(go, popupOpen = false)
        }
        assertNull(s.pending)
    }

    @Test
    fun confirm_ReturnsTheRequestedComponentAndClears() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.consumeOpenRequest()
        s.sync(go, popupOpen = true)
        assertSame(canvas, s.confirm(go))
        assertNull(s.pending)
        assertNull(s.confirm(go), "a second confirm removes nothing")
    }

    @Test
    fun latestRequestWins_SoTheRightComponentIsRemoved() {
        val s = ComponentRemovalConfirmation()
        s.request(transform)
        s.request(canvas)
        assertTrue(s.consumeOpenRequest())
        assertSame(canvas, s.confirm(go))
    }

    @Test
    fun cancel_ClearsWithoutRemoving() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.consumeOpenRequest()
        s.cancel()
        assertNull(s.pending)
        assertNull(s.confirm(go))
    }

    @Test
    fun popupClosedElsewhere_DropsTheRequest() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.consumeOpenRequest()
        s.sync(go, popupOpen = false)
        assertNull(s.pending)
    }

    @Test
    fun syncBeforeOpen_KeepsTheRequest() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.sync(go, popupOpen = false)
        assertSame(canvas, s.pending)
    }

    @Test
    fun selectionChanged_DropsTheRequest() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.consumeOpenRequest()
        s.sync(GameObject("Other"), popupOpen = true)
        assertNull(s.pending)
    }

    @Test
    fun componentAlreadyRemoved_ConfirmReturnsNull() {
        val s = ComponentRemovalConfirmation()
        s.request(canvas)
        s.consumeOpenRequest()
        go.removeComponent(canvas)
        assertNull(s.confirm(go))
    }
}
