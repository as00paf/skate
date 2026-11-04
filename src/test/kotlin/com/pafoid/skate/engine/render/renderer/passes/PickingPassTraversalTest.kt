package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.NonPickable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PickingPassTraversalTest {

    private fun visited(scene: Scene): List<String> =
        mutableListOf<String>().also { out -> PickingPass.forEachPickable(scene) { out += it.name } }

    @Test
    fun visitsNestedChildModels() {
        val scene = Scene()
        val board = GameObject("Board").also(scene::addChild)
        GameObject("Visual").also(board::addChild).addChild(GameObject("Wheel"))
        scene.addChild(GameObject("Floor"))

        assertEquals(listOf("Board", "Visual", "Wheel", "Floor"), visited(scene))
    }

    @Test
    fun hiddenOrLockedParentPrunesSubtree() {
        val scene = Scene()
        val hidden = GameObject("Hidden").also { it.isVisible = false; scene.addChild(it) }
        hidden.addChild(GameObject("UnderHidden"))
        val locked = GameObject("Locked").also { it.isLocked = true; scene.addChild(it) }
        locked.addChild(GameObject("UnderLocked"))

        assertEquals(emptyList<String>(), visited(scene))
    }

    @Test
    fun nonPickableObjectIsSkippedButItsChildrenStayPickable() {
        val scene = Scene()
        val root = GameObject("Root").also { it.components.add(NonPickable()); scene.addChild(it) }
        root.addChild(GameObject("Child"))

        assertEquals(listOf("Child"), visited(scene))
    }
}
