package com.pafoid.skate.editor.animation

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.TRANSLATION
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-24: the Animator inspector's Play, Reset and timeline do something visible in edit and play mode. */
class AnimatorControlsTest {
    private val skeleton = Skeleton(Bone(0, "Hips", Matrix4f()), 1)
    private val skeletonComponent = SkeletonComponent(SkeletonPose(skeleton))

    /** Hips move from x=0 to x=2 over one second. */
    private val clip = Animation(
        "slide",
        listOf(AnimationChannel(AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f), InterpolationType.LINEAR, 3), "Hips", TRANSLATION)),
        1f, "slide.glb",
    )
    private val animator = Animator(mutableListOf(clip)).apply { currentAnimation = clip }
    private val scene = Scene("S")
    private val systems = SystemManager().apply {
        addSystem(AnimationSystem())
        scene.addChild(GameObject("Hero").addComponent(Transform()).addComponent(skeletonComponent).addComponent(animator))
        loadScene(scene)
    }

    private fun hipsX() = skeletonComponent.pose.localTransforms[0].getTranslation(Vector3f()).x
    private fun paletteX() = skeletonComponent.matrixPalette[0].getTranslation(Vector3f()).x

    private fun useController() {
        val asset = AnimationControllerAsset(
            "c", "idle", listOf(StateDef("idle", "slide"), StateDef("other", "slide")),
            listOf(TransitionDef("idle", "other", listOf(TransitionCondition("go", ConditionOp.TRUE)), duration = 0f)),
        )
        animator.useController(asset, skeleton)
    }

    @Test
    fun EditMode_PlayAnimatesTheSceneView_PauseStops() {
        systems.update(0.25f)
        assertEquals(0f, hipsX(), "nothing moves in edit mode before Play")

        AnimatorControls.togglePlay(animator, running = false)
        assertTrue(AnimatorControls.isPlaying(animator, running = false))
        assertFalse(animator.isPlaying, "the saved clip flag is untouched")
        systems.update(0.25f)
        assertEquals(0.5f, hipsX(), 1e-4f)
        assertEquals(0.5f, paletteX(), 1e-4f, "the skin matrices follow, so the mesh moves")

        AnimatorControls.togglePlay(animator, running = false)
        systems.update(0.25f)
        assertEquals(0.5f, hipsX(), 1e-4f, "paused")
    }

    @Test
    fun EditMode_PlayRunsTheController() {
        useController()
        AnimatorControls.togglePlay(animator, running = false)
        systems.update(0.25f)
        assertEquals(0.25f, animator.stateMachine!!.normalizedTime, 1e-4f)
        assertEquals(0.5f, paletteX(), 1e-4f)
    }

    @Test
    fun ResetAndScrub_UpdateThePoseAtOnce_EvenPaused() {
        AnimatorControls.scrub(animator, skeletonComponent, 0.75f, running = false)
        assertEquals(1.5f, hipsX(), 1e-4f)
        assertEquals(1.5f, paletteX(), 1e-4f)
        val version = skeletonComponent.paletteVersion
        AnimatorControls.reset(animator, skeletonComponent)
        assertEquals(0f, animator.currentTime)
        assertEquals(0f, paletteX(), 1e-4f)
        assertTrue(skeletonComponent.paletteVersion > version, "the renderer re-uploads the palette")
    }

    @Test
    fun PlayMode_PauseFreezesTheController_ResetRestartsIt() {
        useController()
        scene.isRunning = true
        val machine = animator.stateMachine!!
        machine.parameters.setBool("go", true)
        systems.update(0.25f)
        assertEquals("other", machine.currentState)
        assertTrue(AnimatorControls.isPlaying(animator, running = true), "a controller plays by default")

        AnimatorControls.togglePlay(animator, running = true)
        assertTrue(animator.controllerPaused)
        val t = machine.normalizedTime
        systems.update(0.25f)
        assertEquals(t, machine.normalizedTime, "paused: the controller holds its pose")

        machine.parameters.setBool("go", false)
        AnimatorControls.reset(animator, skeletonComponent)
        assertEquals("idle", machine.currentState)
        assertEquals(0f, machine.normalizedTime)
        assertEquals(0f, paletteX(), 1e-4f)
        AnimatorControls.togglePlay(animator, running = true)
        systems.update(0.25f)
        assertEquals(0.25f, machine.normalizedTime, 1e-4f, "playing again")
    }

    @Test
    fun PlayMode_WithoutController_UsesTheClipsOwnFlag() {
        scene.isRunning = true
        AnimatorControls.togglePlay(animator, running = true)
        assertTrue(animator.isPlaying)
        systems.update(0.5f)
        assertEquals(1f, hipsX(), 1e-4f)
        assertTrue(AnimatorControls.showsClipTimeline(animator))
        useController()
        assertFalse(AnimatorControls.showsClipTimeline(animator), "a controller picks its own clips")
    }
}
