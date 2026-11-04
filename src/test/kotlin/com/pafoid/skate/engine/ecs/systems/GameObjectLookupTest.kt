package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GameObjectLookupTest {
    private val scene = Scene("s")
    private val manager = GameObjectManager().also { it.init(scene) }
    private val root = GameObject("root")
    private val child = GameObject("child")
    private val grandChild = GameObject("grandChild")

    init {
        scene.addChild(root); root.addChild(child); child.addChild(grandChild)
    }

    @Test
    fun GameObjectLookup_FindsObjectsAtAnyDepth() {
        assertSame(root, manager.getGameObject(root.uId))
        assertSame(child, manager.getGameObject(child.uId))
        assertSame(grandChild, manager.getGameObject(grandChild.uId))
        assertNull(manager.getGameObject(-42))
    }

    @Test
    fun GameObjectLookup_FindsByNameAtAnyDepth() {
        assertSame(grandChild, manager.getGameObject("grandChild"))
        assertNull(manager.getGameObject("missing"))
    }

    @Test
    fun GameObjectLookup_IndexReusedUntilStructureChanges() {
        manager.getGameObject(child.uId)
        manager.getGameObject(grandChild.uId)
        assertEquals(1, manager.indexRebuildCount)
    }

    @Test
    fun GameObjectLookup_TracksAddRemoveAndReparent() {
        manager.getGameObject(root.uId)

        val late = GameObject("late")
        grandChild.addChild(late)
        assertSame(late, manager.getGameObject(late.uId))

        child.removeChild(grandChild)
        assertNull(manager.getGameObject(grandChild.uId))
        assertNull(manager.getGameObject(late.uId))

        scene.addChild(grandChild)
        assertSame(grandChild, manager.getGameObject(grandChild.uId))
        assertSame(late, manager.getGameObject(late.uId))

        val top = GameObject("top")
        manager.addGameObject(top)
        assertSame(top, manager.getGameObject(top.uId))
        manager.removeGameObject(top)
        assertNull(manager.getGameObject(top.uId))
    }

    @Test
    fun GameObjectLookup_TracksIdChanges() {
        val old = grandChild.uId
        manager.getGameObject(old)
        grandChild.generateUid()
        assertNull(manager.getGameObject(old))
        assertSame(grandChild, manager.getGameObject(grandChild.uId))
    }

    @Test
    fun GameObjectLookup_FollowsSceneSwitch() {
        manager.getGameObject(root.uId)
        val other = Scene("other")
        val go = GameObject("go")
        other.addChild(go)
        manager.init(other)
        assertSame(go, manager.getGameObject(go.uId))
        assertNull(manager.getGameObject(root.uId))
    }
}
