package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.ReparentGameObjectCommand
import com.pafoid.skate.editor.commands.scene.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.DeleteGameObjectCommand
import com.pafoid.skate.editor.events.ViewportAction.Reparent
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ViewportActionHandler
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HierarchyCommandsTest {

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))

    private fun obj(name: String) = GameObject(name).also { it.components.add(Transform()) }

    private fun park(): Scene = Scene("Park").also { s ->
        s.addChild(obj("Floor"))
        s.addChild(obj("Board").also { it.addChild(obj("Visual")) })
        s.addChild(obj("Box"))
    }

    private fun saveAndLoad(scene: Scene): Scene {
        val serializer = Serializer()
        val loaded = serializer.decode<Scene>(serializer.encode(scene))
        SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
            .resolveSceneReferences(loaded)
        return loaded
    }

    private fun Scene.named(name: String) = gameObjects().first { it.name == name }
    private fun GameObject.gameObjects(): List<GameObject> = children.flatMap { listOf(it) + it.gameObjects() }

    /** Every object appears exactly once in the tree, and each child's parent link matches the tree. */
    private fun assertConsistent(scene: Scene) {
        val all = scene.gameObjects()
        assertEquals(all.size, all.toSet().size, "object listed twice: ${all.map { it.name }}")
        fun check(node: GameObject) = node.children.forEach { assertSame(node, it.parent, it.name) }
        check(scene); all.forEach(::check)
    }

    @Test
    fun reparentTopLevelUnderAnotherAndBackRoundTrips() {
        listOf(park(), saveAndLoad(park())).forEach { scene ->
            val box = scene.named("Box")
            val board = scene.named("Board")
            undo.executeCommand(ReparentGameObjectCommand(box, board, scene))
            assertSame(board, box.parent)
            assertFalse(box in scene.children)
            assertConsistent(scene)

            undo.undo()
            assertTrue(box.isTopLevel)
            assertEquals(listOf("Floor", "Board", "Box"), scene.children.map { it.name }) // original position
            assertConsistent(scene)

            undo.redo()
            assertSame(board, box.parent)
            assertConsistent(scene)
        }
    }

    @Test
    fun reparentChildToSceneMakesItTopLevelAndUndoRestoresIt() {
        listOf(park(), saveAndLoad(park())).forEach { scene ->
            val visual = scene.named("Visual")
            undo.executeCommand(ReparentGameObjectCommand(visual, scene, scene))
            assertTrue(visual.isTopLevel)
            assertConsistent(scene)
            undo.undo()
            assertSame(scene.named("Board"), visual.parent)
            assertConsistent(scene)
        }
    }

    @Test
    fun cyclesAndNoOpMovesAreInvalid() {
        val scene = park()
        val board = scene.named("Board")
        val visual = scene.named("Visual")
        assertFalse(ReparentGameObjectCommand.isValid(board, visual)) // into own child
        assertFalse(ReparentGameObjectCommand.isValid(board, board))
        assertFalse(ReparentGameObjectCommand.isValid(visual, board)) // already its parent
        assertFalse(ReparentGameObjectCommand.isValid(board, scene)) // already top-level
        assertTrue(ReparentGameObjectCommand.isValid(visual, scene))
        assertTrue(ReparentGameObjectCommand.isValid(scene.named("Box"), visual))
    }

    @Test
    fun hierarchyDragDropEventsReparentThroughTheHandlerOnALoadedScene() {
        val scene = saveAndLoad(park())
        val events = EventSystem()
        val sceneManager = mockk<SceneManager>(relaxed = true) { every { currentScene } returns scene }
        val engine = mockk<Engine>(relaxed = true) {
            every { eventSystem } returns events
            every { this@mockk.sceneManager } returns sceneManager
            every { gameObjectManager } returns GameObjectManager().also { it.init(scene) }
        }
        ViewportActionHandler(engine, undo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)).init()

        val box = scene.named("Box")
        val board = scene.named("Board")
        events.publish(Reparent(box, scene.named("Visual"))) // drop on a nested row
        assertEquals(listOf(scene.named("Visual"), board), box.ancestors().toList()) // nearest first
        events.publish(Reparent(board, box)) // drop onto own descendant: rejected
        assertTrue(board.isTopLevel)
        events.publish(Reparent(box, scene)) // drop on the scene header
        assertTrue(box.isTopLevel)
        assertEquals(2, undo.getUndoCount())
        assertConsistent(scene)
    }

    @Test
    fun deleteNestedChildAndUndoPutsItBackInPlace() {
        listOf(park(), saveAndLoad(park())).forEach { scene ->
            val manager = GameObjectManager().also { it.init(scene) }
            val board = scene.named("Board")
            val second = obj("Second").also(board::addChild)
            val visual = scene.named("Visual")
            undo.executeCommand(DeleteGameObjectCommand(visual, scene, manager))
            assertEquals(listOf(second), board.children)
            undo.undo()
            assertEquals(listOf(visual, second), board.children)
            assertConsistent(scene)
        }
    }

    @Test
    fun createEmptyChildLandsUnderItsParentOnly() {
        val scene = saveAndLoad(park())
        val manager = GameObjectManager().also { it.init(scene) }
        val child = obj("GameObject")
        val board = scene.named("Board")
        undo.executeCommand(CreateGameObjectCommand(child, scene, manager, board))
        assertSame(board, child.parent)
        assertFalse(child in scene.children)
        assertConsistent(scene)
        undo.undo()
        assertFalse(child in board.children)
    }
}
