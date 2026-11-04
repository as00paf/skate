package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ControllerPersistenceTest {

    private val skeleton = Skeleton(Bone(0, "root", Matrix4f()), 1)
    private val logger = LoggerService()

    private fun animator(vararg clips: String) = Animator(
        animations = clips.map { Animation(it, emptyList(), 1f, "$it.glb") }.toMutableList(),
    )

    private fun controller(clip: String) = AnimationControllerAsset(
        "c", "idle", listOf(StateDef("idle", clip)), emptyList(),
    )

    @Test
    fun `Controller_ResolvesFromSavedPathAndBuildsStateMachine`(@TempDir dir: File) {
        val file = File(dir, "c.json").apply { writeText(controller("idle").toJson()) }
        val animator = animator("idle")
        animator.controllerPath = file.path

        assertTrue(animator.resolveController(logger, skeleton))
        assertNotNull(animator.stateMachine)
        assertNotNull(animator.parameters)
        assertEquals("idle", animator.stateMachine!!.currentState)
    }

    @Test
    fun `Controller_MissingFileOrUnknownClipLeavesNoStateMachine`(@TempDir dir: File) {
        val animator = animator("idle")
        animator.controllerPath = File(dir, "nope.json").path
        assertFalse(animator.resolveController(logger, skeleton))
        assertNull(animator.stateMachine)

        val bad = File(dir, "bad.json").apply { writeText(controller("ghost").toJson()) }
        animator.controllerPath = bad.path
        assertFalse(animator.resolveController(logger, skeleton))
        assertNull(animator.stateMachine)

        bad.writeText("{ not json")
        assertFalse(animator.resolveController(logger, skeleton))
    }

    @Test
    fun `Controller_ClearDropsPathAndMachine`(@TempDir dir: File) {
        val file = File(dir, "c.json").apply { writeText(controller("idle").toJson()) }
        val animator = animator("idle")
        animator.controllerPath = file.path
        animator.resolveController(logger, skeleton)
        animator.clearController()
        assertNull(animator.controllerPath)
        assertNull(animator.stateMachine)
    }
}
