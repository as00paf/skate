package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-20: the geometry, shadow and picking passes share one whole-hierarchy walk. */
class RenderTraversalTest {

    private fun visited(scene: Scene) = mutableListOf<String>().also { out -> RenderTraversal.forEachRendered(scene) { out += it.name } }

    @Test
    fun RenderTraversal_VisitsEveryDepthParentsFirst() {
        val scene = Scene()
        val board = GameObject("Skateboard").also(scene::addChild)
        val visual = GameObject("Visual").also(board::addChild)
        visual.addChild(GameObject("Wheel"))
        scene.addChild(GameObject("Floor"))

        assertEquals(listOf("Skateboard", "Visual", "Wheel", "Floor"), visited(scene))
    }

    @Test
    fun RenderTraversal_HiddenDisabledOrDeadParentPrunesItsSubtree() {
        val scene = Scene()
        GameObject("Hidden").also { it.isVisible = false; scene.addChild(it) }.addChild(GameObject("UnderHidden"))
        GameObject("Disabled").also { it.isEnabled = false; scene.addChild(it) }.addChild(GameObject("UnderDisabled"))
        GameObject("Dead").also { it.isDead = true; scene.addChild(it) }.addChild(GameObject("UnderDead"))
        scene.addChild(GameObject("Shown"))

        assertEquals(listOf("Shown"), visited(scene))
    }
}
