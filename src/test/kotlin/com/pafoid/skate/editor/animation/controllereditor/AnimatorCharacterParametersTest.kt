package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateGameplay
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** ANI-26: the Animator window lists the parameters the preview character writes (GPL-31) and declares them. */
class AnimatorCharacterParametersTest {
    @TempDir lateinit var project: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))

    private val asset = AnimationControllerAsset(
        "c", "idle", listOf(StateDef("idle", ""), StateDef("move", "")),
        // `turn` is used by a condition but not declared: it still shows as written by the character.
        listOf(TransitionDef("idle", "move", listOf(TransitionCondition("turn", ConditionOp.GREATER, 0.5f)))),
        parameters = listOf(ParameterDef("speed", ParameterType.FLOAT)),
    )

    private fun window(written: ((Animator?) -> List<ParameterDef>)? = null): AnimatorWindowController {
        val resolve = { p: String ->
            when {
                File(p).isFile -> ResolvedController(p, File(p), File(p).readText())
                p == Assets.Configs.SKATER_CONTROLLER -> ResolvedController(p, null, com.pafoid.skate.engine.gameplay.CharacterConfigs.readText(p)!!)
                else -> null
            }
        }
        return if (written == null) AnimatorWindowController(execute = { undo.executeCommand(it) }, resolve = resolve)
        else AnimatorWindowController(execute = { undo.executeCommand(it) }, resolve = resolve, writtenParameters = written)
    }

    private fun opened(w: AnimatorWindowController, a: AnimationControllerAsset = asset) = w.also {
        val file = File(project, "c.controller.json").apply { writeText(a.toJson()) }
        assertTrue(it.open(file.absolutePath))
    }

    /** A skater the way the scene builds one: an Animator next to a PlayerController running the skate behavior. */
    private fun skater(): Animator {
        SkateGameplay.register()
        val animator = Animator()
        GameObject("Skater").addComponent(animator).addComponent(PlayerController(SkateBehavior.ID, ""))
        return animator
    }

    @Test
    fun Acceptance_ListsTheSkatersParametersAndDeclaresThem() {
        val w = opened(window())
        w.preview = skater()
        val written = SkateBehavior.ANIMATION_PARAMETERS
        assertEquals(written.filter { it.name != "speed" }, w.characterParameters(), "every written parameter but the declared speed")

        val first = w.characterParameters().first()
        assertTrue(w.declareCharacterParameter(first.name))
        assertEquals(first, w.asset!!.parameters.last(), "declared with the character's type and default")
        assertFalse(first in w.characterParameters())
        undo.undo()
        assertTrue(first in w.characterParameters(), "undo brings the suggestion back")

        val before = undo.getUndoCount()
        assertTrue(w.declareAllCharacterParameters())
        assertEquals(before + 1, undo.getUndoCount(), "declare all is one step")
        assertTrue(w.characterParameters().isEmpty())
        assertEquals(written.map { it.name }.toSet(), w.asset!!.parameters.map { it.name }.toSet())
        assertTrue(w.isDeclared("turn"), "the condition's parameter is now declared")
        assertFalse(w.declareAllCharacterParameters(), "nothing left")
    }

    @Test
    fun NoCharacter_NoSuggestions() {
        val w = opened(window())
        assertTrue(w.characterParameters().isEmpty(), "no preview")
        w.preview = Animator().also { GameObject("Prop").addComponent(it) }
        assertTrue(w.characterParameters().isEmpty(), "a preview without a character behind it")
        assertFalse(w.declareCharacterParameter("speed"))
    }

    @Test
    fun Suggestions_SkipDuplicatesAndUnknownNames() {
        val w = opened(window { listOf(ParameterDef("lean", ParameterType.FLOAT, 0.5f), ParameterDef("lean", ParameterType.BOOL), ParameterDef("speed", ParameterType.FLOAT)) })
        w.preview = Animator()
        assertEquals(listOf(ParameterDef("lean", ParameterType.FLOAT, 0.5f)), w.characterParameters())
        assertFalse(w.declareCharacterParameter("nope"))
        assertEquals(0, undo.getUndoCount())
    }

    @Test
    fun ReadOnlyController_RefusesToDeclare() {
        val w = window()
        w.preview = skater()
        assertTrue(w.open(Assets.Configs.SKATER_CONTROLLER))
        assertEquals(SkateBehavior.ANIMATION_PARAMETERS, w.characterParameters(), "the bundled skater controller declares none")
        assertFalse(w.declareCharacterParameter(w.characterParameters().first().name))
        assertEquals(AnimatorWindowController.READ_ONLY, w.message)
        assertEquals(0, undo.getUndoCount())
    }
}
