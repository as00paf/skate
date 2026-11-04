package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameObjectManagerLifecycleTest {
    private val scene = Scene("s")
    private val manager = GameObjectManager().also { it.init(scene) }

    @Test
    fun GameObjectManagerLifecycle_ReAddAfterRemoveRevivesObject() {
        val root = GameObject("root").also(manager::addGameObject)
        val child = GameObject("child").also { manager.addGameObject(it, root) }
        manager.removeGameObject(child)
        assertTrue(child.isDead)

        manager.addGameObject(child, root, 0)

        assertFalse(child.isDead)
        assertSame(root, child.parent)
    }

    @Test
    fun GameObjectManagerLifecycle_PrunedDeadObjectIsDetached() {
        val go = GameObject("go").also(manager::addGameObject)
        scene.isRunning = true
        go.destroy()

        manager.update(0f)

        assertFalse(go in scene.children)
        assertNull(go.parent)
        assertFalse(go.isTopLevel)
    }

    @Test
    fun GameObjectManagerLifecycle_LinkChildrenRefreshesIdIndex() {
        val parent = GameObject("parent")
        val child = GameObject("child")
        scene.children.add(parent) // as decoding does: no parent links, no change notification
        manager.getGameObject(parent.uId)
        parent.children.add(child)

        scene.linkChildren()

        assertSame(child, manager.getGameObject(child.uId))
        assertSame(parent, child.parent)
        assertTrue(parent.isTopLevel)
    }
}
