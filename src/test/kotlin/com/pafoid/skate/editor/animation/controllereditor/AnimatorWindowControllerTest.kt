package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.animation.ControllerAction
import com.pafoid.skate.editor.commands.animation.ControllerEditCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.graph.AnimEventDef
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.NodePos
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import io.mockk.mockk
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnimatorWindowControllerTest {

    @TempDir
    lateinit var project: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
    private val skeleton = Skeleton(Bone(0, "root", Matrix4f()), 1)
    private val logger = LoggerService()
    private val saved = mutableListOf<String>()
    private val assigned = mutableListOf<String>()

    private val animationsDir get() = File(project, "Assets/Animations")

    private fun clip(name: String, duration: Float = 1f) = Animation(name, emptyList(), duration, "$name.glb")

    private fun controller(projectClips: List<ProjectClip> = emptyList()) = AnimatorWindowController(
        execute = { undo.executeCommand(it) },
        resolve = { path ->
            val file = File(path)
            when {
                file.isFile -> ResolvedController(path, file, file.readText())
                path == Assets.Configs.SKATER_CONTROLLER -> ResolvedController(
                    path, null, javaClass.classLoader.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8),
                )
                else -> null
            }
        },
        animationsDir = { animationsDir },
        projectClips = { projectClips },
        onSaved = { saved += it.path },
        assignController = { animator, path -> animator.controllerPath = path; assigned += path },
    )

    private fun opened(asset: AnimationControllerAsset): AnimatorWindowController {
        val file = File(animationsDir, "c.controller.json").apply { parentFile.mkdirs(); writeText(asset.toJson()) }
        return controller().also { assertTrue(it.open(file.absolutePath)) }
    }

    private val twoStates = AnimationControllerAsset(
        "c", "a", listOf(StateDef("a", "x"), StateDef("b", "y")),
        listOf(TransitionDef("a", "b", hasExitTime = true)),
        layout = mapOf("a" to NodePos(0f, 0f), "b" to NodePos(200f, 0f)),
    )

    /** ANI-21 "Done when": a controller built from scratch in the window drives a character. */
    @Test
    fun Acceptance_BuildFromScratch_SaveAndDriveACharacter() {
        val jumpFile = ProjectClip("jump", "Assets/Animations/jump.skanim")
        val w = controller(listOf(jumpFile))
        w.preview = Animator(animations = mutableListOf(clip("idle"), clip("walk")))
        assertTrue(w.newController("Hero"))
        val doc = w.document!!
        assertEquals("Hero.controller.json", doc.file!!.name)
        assertTrue(doc.file.isFile, "New writes the file")

        assertTrue(w.renameState("Idle", "idle"))
        val walk = w.addState(NodePos(220f, 0f))!!
        assertTrue(w.renameState(walk, "walk"))
        val jump = w.addStateFromClip(w.clipOptions().first { it.name == "jump" }, NodePos(440f, 0f))!!
        assertEquals("jump", jump)
        assertTrue(w.setClip("idle", w.clipOptions().first { it.name == "idle" }))
        assertTrue(w.setClip("walk", w.clipOptions().first { it.name == "walk" }))
        assertEquals(listOf("Assets/Animations/jump.skanim"), w.asset!!.clips, "a project clip is listed in the controller")

        val speed = w.addParameter(ParameterType.FLOAT)!!
        assertTrue(w.renameParameter(speed, "speed"))
        val trig = w.addParameter(ParameterType.TRIGGER)!!
        assertTrue(w.renameParameter(trig, "kick"))

        w.startConnect("idle")
        val t0 = w.connectTo("walk")!!
        assertTrue(w.updateTransition(t0) { it.copy(hasExitTime = false) })
        assertTrue(w.addCondition(t0))
        assertTrue(w.setCondition(t0, 0, TransitionCondition("speed", ConditionOp.GREATER, 1f)))
        w.startConnect("walk")
        val t1 = w.connectTo("jump")!!
        assertTrue(w.updateTransition(t1) { it.copy(hasExitTime = false, duration = 0f) })
        assertTrue(w.addCondition(t1))
        assertTrue(w.setCondition(t1, 0, TransitionCondition("kick", ConditionOp.GREATER)))
        assertEquals(ConditionOp.TRIGGER, w.asset!!.transitions[t1].conditions.single().op, "a trigger only offers 'is set'")

        assertTrue(w.errors().isEmpty(), w.errors().joinToString())
        assertTrue(doc.dirty)
        assertTrue(w.save())
        assertFalse(doc.dirty)
        assertEquals(listOf(doc.path), saved, "save asks for the characters to be rebuilt")

        // A character picks the file up and follows the graph.
        val character = Animator(animations = mutableListOf(clip("idle"), clip("walk")))
        character.controllerPath = doc.file.absolutePath
        assertTrue(character.resolveController(logger, skeleton, loadClip = { if (it == jumpFile.path) clip("jump") else null }))
        val machine = character.stateMachine!!
        assertEquals("idle", machine.currentState)
        machine.update(0.1f)
        assertEquals("idle", machine.currentState)
        machine.parameters.setFloat("speed", 2f)
        repeat(20) { machine.update(0.05f) }
        assertEquals("walk", machine.currentState)
        machine.parameters.setTrigger("kick")
        machine.update(0.05f)
        assertEquals("jump", machine.currentState)
    }

    @Test
    fun EngineDefault_IsReadOnly_AndAProjectCopyIsEditable() {
        val w = controller()
        val skater = Animator(animations = mutableListOf()).apply { controllerPath = Assets.Configs.SKATER_CONTROLLER }
        assertTrue(w.openFor(skater))
        assertTrue(w.document!!.readOnly)
        assertNull(w.addState(NodePos(0f, 0f)))
        assertEquals(AnimatorWindowController.READ_ONLY, w.message)
        assertEquals(0, undo.getUndoCount())
        assertFalse(w.canUseOnPreview)

        assertTrue(w.makeProjectCopy())
        val copy = w.document!!
        assertFalse(copy.readOnly)
        assertEquals("skater.controller.json", copy.file!!.name)
        assertEquals(File(animationsDir, "skater.controller.json").absoluteFile, copy.file.absoluteFile)
        assertTrue(w.canUseOnPreview)
        w.usePreviewOnDocument()
        assertEquals(listOf(copy.file.absolutePath), assigned)
        assertFalse(w.canUseOnPreview)
        assertNotNull(w.addState(NodePos(0f, 0f)))
    }

    @Test
    fun Edits_UndoAndRedo_AndRefusedEditsAddNoHistory() {
        val w = opened(twoStates)
        assertFalse(w.renameState("a", "b"))
        assertTrue(w.message!!.contains("already exists"))
        assertEquals(0, undo.getUndoCount())

        assertTrue(w.renameState("a", "start"))
        assertEquals("start", w.asset!!.defaultState)
        assertEquals(NodePos(0f, 0f), w.asset!!.layout["start"], "the layout follows a rename")
        assertEquals("start", w.asset!!.transitions[0].from)
        undo.undo()
        assertEquals("a", w.asset!!.defaultState)
        undo.redo()
        assertEquals("start", w.asset!!.defaultState)
        val command = undo.getUndoHistory().last() as ControllerEditCommand
        assertEquals("command.ControllerEditCommand.rename_state", command.displayNameKey)
    }

    @Test
    fun Delete_DropsTransitions_RefusesTheLastState_AndAnyStateDropsItsOwn() {
        val w = opened(twoStates.copy(transitions = twoStates.transitions + TransitionDef(null, "a", listOf(TransitionCondition("go", ConditionOp.TRUE)))))
        w.select("b")
        assertTrue(w.deleteSelection())
        assertEquals(listOf("a"), w.asset!!.states.map { it.name })
        assertEquals(1, w.asset!!.transitions.size, "a -> b went with b")
        assertTrue("b" !in w.asset!!.layout)
        w.select(ANY_STATE_NODE)
        assertTrue(w.deleteSelection())
        assertTrue(w.asset!!.transitions.isEmpty())
        w.select("a")
        assertFalse(w.deleteSelection())
        assertTrue(w.message!!.contains("at least one state"))
    }

    @Test
    fun Duplicate_WithAndWithoutTransitions() {
        val w = opened(twoStates)
        w.select("b")
        assertEquals(listOf("b 2"), w.duplicateSelection())
        assertEquals(1, w.asset!!.transitions.size, "plain Duplicate copies no transition (Q3)")
        assertEquals(NodePos(230f, 30f), w.asset!!.layout["b 2"])
        assertEquals(setOf("b 2"), w.selectedNodes)
        w.select("b")
        assertEquals(listOf("b 3"), w.duplicateSelection(withTransitions = true))
        assertTrue(w.asset!!.transitions.any { it.from == "a" && it.to == "b 3" })
    }

    @Test
    fun Drag_IsOneCommand() {
        val w = opened(twoStates)
        w.select("a")
        w.select("b", additive = true)
        w.beginDrag("a")
        w.dragTo(10f, 0f)
        w.dragTo(40f, 20f)
        assertEquals(NodePos(240f, 20f), w.asset!!.layout["b"], "previewed live")
        w.endDrag()
        assertEquals(1, undo.getUndoCount())
        assertEquals(ControllerAction.MOVE_STATES, (undo.getUndoHistory().single() as ControllerEditCommand).action)
        undo.undo()
        assertEquals(NodePos(0f, 0f), w.asset!!.layout["a"])
    }

    @Test
    fun Parameters_RenameFollowsConditions_RemoveRefusedWhileUsed() {
        val w = opened(twoStates.copy(transitions = listOf(TransitionDef("a", "b", listOf(TransitionCondition("go", ConditionOp.TRUE))))))
        assertEquals(listOf("go"), w.parameters().map { it.name }, "undeclared names are inferred")
        assertFalse(w.isDeclared("go"))
        assertTrue(w.setParameter("go", ParameterType.BOOL, 1f))
        assertTrue(w.isDeclared("go"), "editing an inferred parameter declares it")
        assertTrue(w.renameParameter("go", "ready"))
        assertEquals("ready", w.asset!!.transitions[0].conditions[0].parameter)
        assertFalse(w.removeParameter("ready"))
        assertTrue(w.message!!.contains("a -> b"))
        assertTrue(w.removeCondition(0, 0))
        assertTrue(w.removeParameter("ready"))
    }

    @Test
    fun RequestTransition_DeclaresTheBool_AndTagsIt() {
        val w = opened(twoStates)
        val index = w.addRequestTransition("a", "b")!!
        assertEquals(TransitionCondition("b", ConditionOp.TRUE), w.asset!!.transitions[index].conditions.single())
        val p = w.parameters().single { it.name == "b" }
        assertTrue(w.isRequest(p))
        assertEquals(index, w.selectedTransition)
    }

    @Test
    fun TransitionPriority_MovesAmongTheSameSource() {
        val w = opened(twoStates.copy(transitions = listOf(
            TransitionDef("a", "b", hasExitTime = true),
            TransitionDef("b", "a", hasExitTime = true),
            TransitionDef("a", "b", listOf(TransitionCondition("go", ConditionOp.TRUE))),
        )))
        assertEquals(listOf(0, 2), w.transitionsFrom("a"))
        w.selectTransition(2)
        assertTrue(w.moveTransition(2, -1))
        assertEquals("go", w.asset!!.transitions[0].conditions.single().parameter)
        assertEquals(0, w.selectedTransition)
        assertFalse(w.moveTransition(0, -1), "already first")
    }

    @Test
    fun CopyPaste_KeepsInnerTransitions() {
        val w = opened(twoStates)
        w.selectNodes(setOf("a", "b"))
        assertTrue(w.copySelection())
        assertTrue(w.paste())
        assertEquals(listOf("a", "b", "a 2", "b 2"), w.asset!!.states.map { it.name })
        assertTrue(w.asset!!.transitions.any { it.from == "a 2" && it.to == "b 2" })
        assertEquals(NodePos(40f, 40f), w.asset!!.layout["a 2"])
        assertEquals(1, undo.getUndoCount())
    }

    @Test
    fun StateEvents_AndSave_UndoMakesItDirtyAgain() {
        val w = opened(twoStates)
        assertTrue(w.addEvent("a", 0.5f, "step"))
        assertTrue(w.updateEvent("a", 0, AnimEventDef(1.4f, "land")))
        assertEquals(AnimEventDef(1f, "land"), w.asset!!.states[0].events.single(), "times stay normalized")
        assertTrue(w.save())
        assertFalse(w.document!!.dirty)
        val back = AnimationControllerAsset.fromJson(w.document!!.file!!.readText())
        assertEquals(w.asset, back)
        undo.undo()
        assertTrue(w.document!!.dirty)
        assertFalse(File(w.document!!.file!!.path + ".tmp").exists())
    }

    @Test
    fun ReopeningKeepsTheDocument_AndPreviewLinkFindsTheMachine() {
        val w = opened(twoStates)
        val doc = w.document!!
        w.addState(NodePos(0f, 100f))
        assertTrue(w.open(doc.file!!.absolutePath))
        assertTrue(w.document === doc)
        val character = Animator(animations = mutableListOf(clip("x"), clip("y")))
        character.controllerPath = doc.file.absolutePath
        assertTrue(character.resolveController(logger, skeleton))
        w.preview = character
        assertNotNull(w.liveMachine)
        val go = w.parameters().firstOrNull()
        assertNull(go)
        character.controllerPath = "elsewhere.controller.json"
        assertNull(w.liveMachine)
    }

    @Test
    fun ClipOptions_CharacterThenControllerThenProjectThenPoses() {
        val w = controller(listOf(ProjectClip("x", "Assets/Animations/x.skanim"), ProjectClip("kick", "Assets/Animations/kick.skanim")))
        val file = File(animationsDir, "c.controller.json").apply {
            parentFile.mkdirs()
            writeText(twoStates.copy(clips = listOf("Assets/Animations/kick.skanim")).toJson())
        }
        w.open(file.absolutePath)
        w.preview = Animator(animations = mutableListOf(clip("x")))
        val options = w.clipOptions()
        assertEquals(listOf("x" to ClipSource.CHARACTER, "kick" to ClipSource.CONTROLLER), options.map { it.name to it.source })
        assertTrue(w.errors().any { it.contains("'y'") }, "b plays y, which the preview does not have")
    }

    @Test
    fun EveryAction_IsTranslated() {
        val props = listOf("assets/strings/editor/animation.properties", "assets/strings/editor/animation_fr.properties").map { path ->
            java.util.Properties().apply { File(path).reader(Charsets.UTF_8).use { load(it) } }
        }
        for (a in ControllerAction.entries) for (p in props) {
            assertNotNull(p.getProperty("command.ControllerEditCommand.${a.key}"), a.key)
        }
    }

    @Suppress("unused")
    private fun Command.name() = getDisplayName()
}
