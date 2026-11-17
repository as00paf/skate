package com.pafoid.skate.editor.animation

import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.CharacterAnimationDriver
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Matrix4f
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ANI-25 on the real skater: the Skater prefab with its real clips (the engine's FBX files) and its bundled
 * controller, driven every frame by the real SkateBehavior through CharacterAnimationDriver, as PlayerMotionSystem
 * does in play mode. A clip picked in Properties > Animator must show and win over the controller and gameplay,
 * then hand back on Stop / Reset.
 */
class SkaterInspectorPlaybackTest {
    private val dt = 1f / 60f
    private val clips: List<Animation> = Skater.DEFAULT_ANIMATIONS.map { AnimationLoader().loadAnimations(it.replace("engine:", "assets/")).first() }
    private val skater: Skater = Skater("Skater", `3dModel`(skeleton = rig()), animations = clips)
    private val animator: Animator = skater.animator
    private val skeleton: SkeletonComponent = skater.skeletonComponent
    private val scene = Scene("S").apply { addChild(skater) }
    private val systems = SystemManager().apply { addSystem(AnimationSystem()); loadScene(scene) }
    private val behavior = SkateBehavior(SkateControllerConfig())
    private var gameplayState = ""

    init {
        assertTrue(animator.resolveController(LoggerService(), skeleton.pose.skeleton), "the bundled skater controller builds")
    }

    /**
     * The humanoid test rig with the bone names the model loader gives (BoneNameMapper), plus every other bone the
     * real clips animate (fingers, head top...) under the hips, like the real James model has them.
     */
    private fun rig(): Skeleton {
        val src = TestHumanoid.skeleton()
        fun copy(b: Bone): Bone = Bone(b.index, BoneNameMapper.map(b.name), Matrix4f(b.bindLocalTransform)).also { c -> b.children.forEach { c.addChild(copy(it)) } }
        val root = copy(src.rootBone)
        val names = HashSet<String>()
        fun collect(b: Bone) { names += b.name; b.children.forEach(::collect) }
        collect(root)
        var next = src.boneCount
        for (name in clips.flatMap { c -> c.channels.map { BoneNameMapper.map(it.targetNodeName) } }.distinct()) {
            if (names.add(name)) root.addChild(Bone(next++, name, Matrix4f()))
        }
        return Skeleton(root, next)
    }

    private fun clip(name: String): Animation = clips.single { it.name == name }

    /** One game frame: gameplay drives the animator (walking forward fast), then the animation system runs. */
    private fun frame(withGameplay: Boolean = true) {
        if (withGameplay) {
            val motion = behavior.update(CharacterInput(move = Vector2f(0f, 1f), sprint = true, horizontalSpeed = 4f), dt)
            gameplayState = CharacterAnimationDriver.drive(behavior, motion, gameplayState, animator)
        }
        systems.update(dt)
    }

    /** Hips local transform the clip gives at [time], sampled on a fresh rig. */
    private fun clipHips(clip: Animation, time: Float): Matrix4f {
        val rig = rig()
        clip.update(time, rig)
        return Matrix4f(rig.getBoneByName("Hips")!!.localTransform)
    }

    private val hipsIndex: Int get() = skeleton.pose.skeleton.getBoneByName("Hips")!!.index

    private fun hips(): Matrix4f = Matrix4f(skeleton.pose.localTransforms[hipsIndex])

    private fun assertPose(expected: Matrix4f, message: String) =
        assertTrue(hips().equals(expected, 1e-4f), "$message\nexpected $expected\nbut was ${hips()}")

    @Test
    fun PlayMode_APickedClipWinsOverGameplay_StopHandsBack() {
        scene.isRunning = true
        repeat(30) { frame() }
        val machine = animator.stateMachine!!
        val gameplayPick = machine.currentState
        assertNotEquals(machine.controller.defaultState, gameplayPick, "gameplay drove the controller away from its default")

        // What the inspector did before ANI-25: play(clip) is ignored by the controller.
        animator.play(clip("jump"))
        repeat(10) { frame() }
        assertFalse(hips().equals(clipHips(clip("jump"), 10 * dt), 1e-4f), "repro: without the override the clip never shows")

        AnimatorControls.selectClip(animator, clip("jump"), skeleton, running = true)
        repeat(20) { frame() }
        assertEquals(20 * dt, animator.inspectorTime, 1e-4f)
        assertPose(clipHips(clip("jump"), animator.inspectorTime), "the picked clip drives the skater while gameplay runs")
        assertEquals(gameplayPick, machine.currentState, "the controller waits; gameplay's writes only queue in it")

        AnimatorControls.togglePlay(animator, running = true)
        val held = animator.inspectorTime
        repeat(5) { frame() }
        assertEquals(held, animator.inspectorTime, "Pause holds the clip")

        AnimatorControls.stop(animator, skeleton)
        assertNull(animator.inspectorClip)
        AnimatorControls.togglePlay(animator, running = true)
        repeat(3) { frame() }
        assertTrue(hips().equals(Matrix4f().also { machine.pose.toMatrix(hipsIndex, it) }, 1e-4f), "the controller drives again")
    }

    @Test
    fun PlayMode_ResetHandsBackAndRestartsTheController() {
        scene.isRunning = true
        repeat(30) { frame() }
        AnimatorControls.selectClip(animator, clip("running"), skeleton, running = true)
        repeat(5) { frame() }
        AnimatorControls.reset(animator, skeleton)
        assertNull(animator.inspectorClip)
        val machine = animator.stateMachine!!
        assertEquals(machine.controller.defaultState, machine.currentState)
        assertEquals(0f, machine.normalizedTime)
    }

    @Test
    fun EditMode_APickedClipPlaysInTheSceneView() {
        repeat(5) { frame(withGameplay = false) }
        val before = hips()
        AnimatorControls.selectClip(animator, clip("running"), skeleton, running = false)
        assertTrue(animator.editPreview, "picking a clip starts the preview")
        assertPose(clipHips(clip("running"), 0f), "shown at once")
        repeat(30) { frame(withGameplay = false) }
        assertPose(clipHips(clip("running"), 30 * dt), "it plays in edit mode")
        assertFalse(hips().equals(before, 1e-4f))

        AnimatorControls.scrub(animator, skeleton, 0.5f, running = false)
        assertFalse(animator.editPreview)
        assertPose(clipHips(clip("running"), 0.5f), "scrubbing shows the clip at that time")

        AnimatorControls.stop(animator, skeleton)
        assertNotNull(animator.stateMachine)
        assertTrue(hips().equals(Matrix4f().also { animator.stateMachine!!.pose.toMatrix(hipsIndex, it) }, 1e-4f), "back to the controller's pose")
    }
}
