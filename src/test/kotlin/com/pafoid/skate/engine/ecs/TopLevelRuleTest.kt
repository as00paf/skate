package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopLevelRuleTest {

    private fun obj(name: String) = GameObject(name).also { it.components.add(Transform()) }

    /** Saves [scene] to JSON and opens it like the editor does (decode + SceneManager). */
    private fun saveAndLoad(scene: Scene): Scene {
        val serializer = Serializer()
        val loaded = serializer.decode<Scene>(serializer.encode(scene))
        SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
            .resolveSceneReferences(loaded)
        return loaded
    }

    @Test
    fun addGameObjectMakesTheObjectTopLevel() {
        val scene = Scene()
        val manager = GameObjectManager().also { it.init(scene) }
        val box = obj("Box")
        manager.addGameObject(box)
        assertSame(scene, box.parent)
        assertTrue(box.isTopLevel)
    }

    @Test
    fun addGameObjectUnderAParentMakesANestedChildOnly() {
        val scene = Scene()
        val manager = GameObjectManager().also { it.init(scene) }
        val root = obj("Root").also(manager::addGameObject)
        val child = obj("Child")
        manager.addGameObject(child, root)
        assertSame(root, child.parent)
        assertFalse(child.isTopLevel)
        assertFalse(child in scene.children)
        assertEquals(listOf(child), root.children)
    }

    @Test
    fun removeGameObjectDetachesNestedChildrenToo() {
        val scene = Scene()
        val manager = GameObjectManager().also { it.init(scene) }
        val root = obj("Root").also(manager::addGameObject)
        val child = obj("Child").also { manager.addGameObject(it, root) }
        manager.removeGameObject(child)
        assertTrue(root.children.isEmpty())
        assertNull(child.parent)
    }

    @Test
    fun sceneLoadedFromDiskHasTheSameTopLevelRule() {
        val scene = Scene("Park")
        val board = obj("Board").also(scene::addChild)
        obj("Visual").also(board::addChild).addChild(obj("Wheel"))
        scene.addChild(obj("Floor"))

        val loaded = saveAndLoad(scene)
        val (loadedBoard, floor) = loaded.children
        val visual = loadedBoard.children.single()
        val wheel = visual.children.single()

        assertTrue(loadedBoard.isTopLevel)
        assertTrue(floor.isTopLevel)
        assertSame(loaded, loadedBoard.parent)
        assertFalse(visual.isTopLevel)
        assertSame(visual, wheel.parent)
        assertEquals(listOf(visual, loadedBoard), wheel.ancestors().toList())
    }

    @Test
    fun ancestorsStopBeforeTheScene() {
        val scene = Scene()
        val a = obj("A").also(scene::addChild)
        val b = obj("B").also(a::addChild)
        assertEquals(listOf(a), b.ancestors().toList())
        assertTrue(a.ancestors().none())
        assertFalse(scene.isTopLevel)
        assertFalse(obj("Detached").isTopLevel)
    }

    @Test
    fun copiedObjectKeepsParentLinksInsideItsSubtree() {
        val root = obj("Root")
        root.addChild(obj("Child").also { it.addChild(obj("Grandchild")) })
        val copy = root.copy(Serializer())
        val child = copy.children.single()
        assertSame(copy, child.parent)
        assertSame(child, child.children.single().parent)
    }
}
