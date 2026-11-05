package com.pafoid.skate.editor.animation

import com.pafoid.skate.editor.commands.objects.SetAnimatorControllerCommand
import com.pafoid.skate.editor.commands.objects.SetAnimatorMaskCommand
import com.pafoid.skate.editor.events.AnimatorAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.AnimatorActionHandler
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimatorMaskDef
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.MaskPreset
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnimatorInspectorTest {
    private val logger = LoggerService()
    private val skeleton = Skeleton(Bone(0, "root", Matrix4f()), 1)

    private fun animator(): Animator {
        val animator = Animator(animations = mutableListOf(Animation("idle", emptyList(), 1f, "idle.fbx"), Animation("run", emptyList(), 1f, "run.fbx")))
        GameObject("Hero").addComponent(SkeletonComponent(SkeletonPose(skeleton))).addComponent(animator)
        return animator
    }

    private fun controllerFile(dir: File, name: String = "hero.controller.json") = File(dir, name).apply {
        writeText(AnimationControllerAsset(
            "hero", "idle", listOf(StateDef("idle", "idle"), StateDef("run", "run")),
            listOf(TransitionDef(null, "run", listOf(TransitionCondition("run", ConditionOp.TRUE)), duration = 0.5f)),
        ).toJson())
    }

    @Test
    fun `AnimatorController_AssignUndoRedoThroughHandler`(@TempDir dir: File) {
        val events = EventSystem()
        val undo = UndoRedoManager(events, logger)
        AnimatorActionHandler(events, undo, logger)
        val animator = animator()
        val path = controllerFile(dir).path

        events.publish(AnimatorAction.SetController(animator, path))
        assertEquals(path, animator.controllerPath)
        assertNotNull(animator.stateMachine)

        undo.undo()
        assertNull(animator.controllerPath)
        assertNull(animator.stateMachine)

        undo.redo()
        assertEquals(path, animator.controllerPath)
        assertNotNull(animator.stateMachine)

        events.publish(AnimatorAction.SetController(animator, null))
        assertNull(animator.stateMachine)
        undo.undo()
        assertNotNull(animator.stateMachine)
    }

    @Test
    fun `AnimatorMask_AssignUndoRedoThroughHandler`() {
        val events = EventSystem()
        val undo = UndoRedoManager(events, logger)
        AnimatorActionHandler(events, undo, logger)
        val animator = animator()
        val upper = AnimatorMaskDef(MaskPreset.UPPER_BODY)

        events.publish(AnimatorAction.SetMask(animator, upper))
        assertEquals(upper, animator.mask)
        undo.undo()
        assertEquals(AnimatorMaskDef(), animator.mask)
        undo.redo()
        assertEquals(upper, animator.mask)
    }

    @Test
    fun `AnimatorCommands_SameValueIsNoOp`() {
        val animator = animator()
        assertTrue(SetAnimatorMaskCommand(animator, AnimatorMaskDef()).isNoOp)
        assertTrue(SetAnimatorControllerCommand(animator, null, logger).isNoOp)
        assertFalse(SetAnimatorMaskCommand(animator, AnimatorMaskDef(MaskPreset.SUBTREE, "LeftArm")).isNoOp)
    }

    @Test
    fun `AnimatorSettings_SurviveSaveAndLoad`(@TempDir dir: File) {
        val animator = animator()
        animator.controllerPath = controllerFile(dir).path
        animator.mask = AnimatorMaskDef(MaskPreset.SUBTREE, "Spine")
        val json = Serializer().json

        val loaded = json.decodeFromString(Animator.serializer(), json.encodeToString(Animator.serializer(), animator))

        assertEquals(animator.controllerPath, loaded.controllerPath)
        assertEquals(animator.mask, loaded.mask)
        assertTrue(loaded.resolveController(logger, skeleton))
    }

    @Test
    fun `ControllerCatalog_ListsBundledProjectAndCurrent`(@TempDir dir: File) {
        val catalog = AnimatorControllerCatalog(listOf("configs/a.controller.json"))
        catalog.projectRoot = dir
        val project = controllerFile(File(dir, "Assets").apply { mkdirs() }).absolutePath
        File(dir, "Assets/not-a-controller.json").writeText("{}")

        assertEquals(listOf("configs/a.controller.json", project, "C:/x/old.json"), catalog.options("C:/x/old.json"))

        val added = controllerFile(dir, "zz.controller.json").absolutePath
        assertFalse(added in catalog.options(null), "cached until refresh")
        catalog.refresh()
        assertTrue(added in catalog.options(null))

        catalog.projectRoot = null
        assertEquals(listOf("configs/a.controller.json"), catalog.options(null))
    }

    @Test
    fun `ControllerCatalog_NamesAndStateReadout`(@TempDir dir: File) {
        assertEquals("skater", AnimatorControllerCatalog.displayName("configs/skater.controller.json"))
        assertEquals("hero", AnimatorControllerCatalog.displayName("C:\\p\\Assets\\hero.controller.json"))
        assertTrue(AnimatorControllerCatalog.isController("a/B.Controller.JSON"))
        assertFalse(AnimatorControllerCatalog.isController("a/b.json"))

        val animator = animator()
        animator.loadController(controllerFile(dir).path, logger)
        val machine = animator.stateMachine!!
        assertEquals("idle", AnimatorControllerCatalog.stateReadout(machine))
        animator.play("run")
        machine.update(0.1f)
        assertEquals("idle -> run", AnimatorControllerCatalog.stateReadout(machine))
    }
}
