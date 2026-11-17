package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.Blend1DDef
import com.pafoid.skate.engine.animation.graph.Blend2DDef
import com.pafoid.skate.engine.animation.graph.BlendTree1D
import com.pafoid.skate.engine.animation.graph.ClipRef
import com.pafoid.skate.engine.animation.graph.NodePos
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import io.mockk.mockk
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** ANI-23 acceptance: a blend tree is created and edited from the Animator window and saved in the controller file. */
class AnimatorBlendTreeTest {
    @TempDir lateinit var project: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
    private val commands get() = undo.getUndoCount()
    private val runFile = ProjectClip("run", "Assets/Animations/run.skanim")

    private fun clip(name: String) = Animation(name, emptyList(), 1f, "$name.glb")

    private val asset = AnimationControllerAsset(
        "c", "move", listOf(StateDef("move", "walk"), StateDef("idle", "")),
        layout = mapOf("move" to NodePos(0f, 0f), "idle" to NodePos(200f, 0f)),
    )

    private fun opened(a: AnimationControllerAsset = asset, preview: Animator? = Animator(animations = mutableListOf(clip("walk"), clip("strafe")))): Pair<AnimatorWindowController, File> {
        val file = File(project, "Assets/Animations/c.controller.json").apply { parentFile.mkdirs(); writeText(a.toJson()) }
        val w = AnimatorWindowController(
            execute = { undo.executeCommand(it) },
            resolve = { p -> File(p).takeIf { it.isFile }?.let { ResolvedController(p, it, it.readText()) } },
            animationsDir = { File(project, "Assets/Animations") },
            projectClips = { listOf(runFile) },
        )
        w.preview = preview
        assertTrue(w.open(file.absolutePath))
        return w to file
    }

    private fun AnimatorWindowController.option(name: String) = clipOptions().first { it.name == name }

    @Test
    fun Acceptance_Create1DTree_EditIt_SaveAndPlayIt() {
        val (w, file) = opened()
        val key = w.createBlendTree("move", BlendKind.ONE_D)!!
        assertEquals("move Blend", key)
        val state = w.asset!!.states.first { it.name == "move" }
        assertEquals(key, state.motion)
        val tree = w.blendTreeOf(state) as Blend1DDef
        assertEquals("speed", tree.parameter, "declares a float when there is none")
        assertEquals(ParameterDef("speed", ParameterType.FLOAT), w.asset!!.parameters.single())
        assertEquals(listOf(ClipRef("walk")), tree.children.map { it.motion }, "the state's clip is the first motion")

        assertTrue(w.addBlendChild(key, w.option("run")))
        assertTrue(w.setBlendThreshold(key, 1, 3f))
        assertEquals(listOf(runFile.path), w.asset!!.clips, "a project clip is listed in the controller")
        assertTrue(w.addBlendChild(key, w.option("strafe")))
        assertTrue(w.setBlendChildClip(key, 2, w.option("walk")))
        assertTrue(w.removeBlendChild(key, 2))
        assertTrue(w.renameBlendTree(key, "locomotion"))
        assertEquals("locomotion", w.asset!!.states.first { it.name == "move" }.motion)

        val edited = w.asset!!.motions["locomotion"] as Blend1DDef
        assertEquals(listOf(0f to "walk", 3f to "run"), edited.children.map { it.threshold to (it.motion as ClipRef).clip })
        assertTrue(w.save())

        // Saved in the file, and a character plays it.
        val reloaded = AnimationControllerAsset.fromJson(file.readText())
        assertEquals(edited, reloaded.motions["locomotion"])
        val character = Animator(animations = mutableListOf(clip("walk")))
        character.controllerPath = file.absolutePath
        assertTrue(character.resolveController(LoggerService(), Skeleton(Bone(0, "root", Matrix4f()), 1), loadClip = { if (it == runFile.path) clip("run") else null }))
        assertTrue(character.stateMachine!!.motionOf("move") is BlendTree1D)
    }

    @Test
    fun Create2DTree_PicksFloatsAndFreeSpots() {
        val (w, _) = opened(asset.copy(parameters = listOf(ParameterDef("vx", ParameterType.FLOAT))))
        val key = w.createBlendTree("move", BlendKind.TWO_D)!!
        var tree = w.asset!!.motions[key] as Blend2DDef
        assertEquals("vx" to "moveY", tree.parameterX to tree.parameterY, "existing floats first, then a new one")
        assertTrue(w.addBlendChild(key, w.option("strafe")))
        assertTrue(w.addBlendChild(key, w.option("run")))
        tree = w.asset!!.motions[key] as Blend2DDef
        assertEquals(listOf(0f to 0f, 0f to 1f, 1f to 0f), tree.children.map { it.x to it.y })
        assertTrue(w.setBlendPosition(key, 2, -1f, 0.5f))
        assertTrue(w.setBlendParameter(key, 1, "vx"))
        tree = w.asset!!.motions[key] as Blend2DDef
        assertEquals(-1f to 0.5f, tree.children[2].x to tree.children[2].y)
        assertEquals("vx", tree.parameterY)
    }

    @Test
    fun Edits_AreUndoableAndRefusalsAddNoHistory() {
        val (w, _) = opened()
        val key = w.createBlendTree("move", BlendKind.ONE_D)!!
        assertEquals(1, commands, "creating is one step (parameter, tree and state together)")
        assertFalse(w.removeBlendChild(key, 0), "a tree keeps one motion")
        assertTrue(w.message!!.isNotBlank())
        assertEquals(1, commands)
        w.addBlendChild(key, w.option("strafe"))
        undo.undo()
        assertEquals(1, (w.asset!!.motions[key] as Blend1DDef).children.size)
        undo.undo()
        assertEquals(asset, w.asset, "undo restores the controller")
        assertEquals(0, undo.getUndoCount())
        undo.redo()
        assertEquals(key, w.asset!!.states.first { it.name == "move" }.motion, "redo brings the tree back")
    }

    @Test
    fun BackToOneClip_DeletesAnUnusedTree() {
        val (w, _) = opened()
        val key = w.createBlendTree("move", BlendKind.ONE_D)!!
        w.updateState("idle") { it.copy(motion = key) }
        assertTrue(w.removeBlendTree("move"))
        assertEquals("walk", w.asset!!.states.first { it.name == "move" }.motion)
        assertTrue(key in w.asset!!.motions, "kept while idle still plays it")
        assertTrue(w.removeBlendTree("idle"))
        assertTrue(w.asset!!.motions.isEmpty())
    }

    @Test
    fun Create_NeedsAClip() {
        val (w, _) = opened(asset.copy(states = listOf(StateDef("move", ""), StateDef("idle", ""))), preview = null)
        // The project still offers run.skanim, so a tree can start from it.
        assertEquals("move Blend", w.createBlendTree("move", BlendKind.ONE_D))
        // No character and no project clips: nothing to blend.
        val bare = AnimatorWindowController(
            execute = { undo.executeCommand(it) },
            resolve = { p -> File(p).takeIf { it.isFile }?.let { ResolvedController(p, it, it.readText()) } },
        )
        val file = File(project, "bare.controller.json").apply { writeText(asset.copy(states = listOf(StateDef("move", ""))).toJson()) }
        assertTrue(bare.open(file.absolutePath))
        assertNull(bare.createBlendTree("move", BlendKind.ONE_D))
        assertEquals(AnimatorWindowController.NO_CLIP, bare.message)
    }

    @Test
    fun Preview_ShowsTheRuntimeWeightsAndDrivesLiveParameters() {
        val (w, _) = opened(asset.copy(parameters = listOf(ParameterDef("speed", ParameterType.FLOAT, default = 1.5f))))
        val key = w.createBlendTree("move", BlendKind.ONE_D)!!
        w.addBlendChild(key, w.option("run"))
        w.setBlendThreshold(key, 1, 3f)
        assertEquals(1.5f to 0f, w.blendPreviewPoint(key), "starts at the parameter's default")
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), w.blendPreviewWeights(key), 1e-6f)
        w.setBlendPreviewPoint(key, 3f)
        assertArrayEquals(floatArrayOf(0f, 1f), w.blendPreviewWeights(key), 1e-6f)
        assertTrue(w.renameBlendTree(key, "loco"))
        assertEquals(3f to 0f, w.blendPreviewPoint("loco"), "the marker follows a rename")
        assertTrue(w.save())

        // Play mode: the preview character runs this controller; the marker reads and writes its parameters.
        val character = w.preview!!
        character.controllerPath = w.document!!.file!!.absolutePath
        assertTrue(character.resolveController(LoggerService(), Skeleton(Bone(0, "root", Matrix4f()), 1), loadClip = { clip("run") }))
        character.stateMachine!!.parameters.setFloat("speed", 0f)
        assertEquals(0f to 0f, w.blendPreviewPoint("loco"))
        w.setBlendPreviewPoint("loco", 2f)
        assertEquals(2f, character.stateMachine!!.parameters.getFloat("speed"))
        assertEquals(commands, undo.getUndoCount(), "previewing never edits")
    }
}
