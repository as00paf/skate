package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.ReparentGameObjectCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompositeCommandTest {
    private val log = mutableListOf<String>()

    private fun step(name: String) = object : Command {
        override fun execute() { log += "do $name" }
        override fun undo() { log += "undo $name" }
        override fun getDisplayName() = name
        override fun getTargetName() = name
    }

    @Test
    fun Composite_RunsInOrderAndUndoesInReverse() {
        val cmd = CompositeCommand("k", "n", listOf({ step("a") }, { step("b") }))
        cmd.execute()
        cmd.undo()
        cmd.execute()
        assertEquals(listOf("do a", "do b", "undo b", "undo a", "do a", "do b"), log)
        assertEquals(listOf<Any>(2), cmd.displayNameArgs)
    }

    @Test
    fun Composite_SkipsNullStepsAndFailsWhenNothingRan() {
        val cmd = CompositeCommand("k", "n", listOf({ null }, { step("b") }))
        cmd.execute()
        assertEquals(1, cmd.commands.size)
        assertTrue(cmd.wasSuccessful())
        val empty = CompositeCommand("k", "n", listOf({ null }))
        empty.execute()
        assertFalse(empty.wasSuccessful())
        val undo = UndoRedoManager(EventSystem(mockk(relaxed = true)), mockk(relaxed = true))
        undo.executeCommand(empty)
        assertEquals(0, undo.getUndoCount())
    }

    @Test
    fun Composite_BuildsEachStepJustBeforeItRuns_SoSiblingMovesUndoInPlace() {
        val scene = Scene("S")
        val a = GameObject("A").also { scene.addChild(it) }
        val b = GameObject("B").also { scene.addChild(it) }
        val c = GameObject("C").also { scene.addChild(it) }
        val target = GameObject("T").also { scene.addChild(it) }
        val undo = UndoRedoManager(EventSystem(mockk(relaxed = true)), mockk(relaxed = true))
        undo.executeCommand(CompositeCommand("k", "n", listOf(a, b, c).map { go -> { ReparentGameObjectCommand(go, target, scene) } }, scene))
        assertEquals(listOf("A", "B", "C"), target.children.map { it.name })
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(listOf("A", "B", "C", "T"), scene.children.map { it.name })
        undo.redo()
        assertEquals(listOf("A", "B", "C"), target.children.map { it.name })
    }

    @Test
    fun Composite_SetsAndRestoresTheSelection() {
        val scene = Scene("S")
        val a = GameObject("A").also { scene.addChild(it) }
        val b = GameObject("B").also { scene.addChild(it) }
        scene.setSelection(listOf(a))
        val cmd = CompositeCommand("k", "n", listOf({ step("x") }), scene, selectAfter = { listOf(a, b) })
        cmd.execute()
        assertEquals(listOf(a, b), scene.selectedGameObjects)
        cmd.undo()
        assertEquals(listOf(a), scene.selectedGameObjects)
    }
}
