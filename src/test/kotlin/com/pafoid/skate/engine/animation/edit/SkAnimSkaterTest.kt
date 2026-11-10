package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.ClipMotion
import com.pafoid.skate.engine.animation.graph.FittedMotion
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.abs

/** ANI-13 acceptance: the real riding clip, saved as `.skanim` and loaded back, plays the same on the skater rig. */
class SkAnimSkaterTest {
    private fun skeleton() = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!!

    private fun poses(clip: Animation): List<Pose> {
        val skeleton = skeleton()
        val motion = ClipMotion.of(clip, skeleton)
        return listOf(0f, 0.1f, 0.33f, 0.5f, 0.77f, 0.99f).map { t -> Pose(skeleton.boneCount).also { motion.evaluate(AnimParameters(), t, it) } }
    }

    @Test
    fun `SkAnim_RidingClipRoundTripsAndPlaysOnTheSkater`(@TempDir dir: File) {
        val original = AnimationLoader().loadAnimations(RIDING)[0]
        val file = File(dir, "skate riding.skanim").apply { writeText(SkAnimFormat.encode(SkAnimFormat.fromAnimation(original))) }
        val loaded = AnimationLoader().loadAnimations(file.path).single()

        assertEquals(original.name, loaded.name)
        assertEquals(original.duration, loaded.duration, 1e-6f)
        assertEquals(original.channels.size, loaded.channels.size)
        val expected = poses(original)
        val actual = poses(loaded)
        for (i in expected.indices) {
            val e = expected[i]; val a = actual[i]
            val worstT = e.translations.indices.maxOf { abs(e.translations[it] - a.translations[it]) }
            val worstR = e.rotations.indices.maxOf { abs(abs(e.rotations[it]) - abs(a.rotations[it])) }
            assertTrue(worstT < 1e-4f && worstR < 1e-4f, "sample $i: translation $worstT, rotation $worstR")
        }

        val stubs = listOf("idle_0", "walking", "running", "jump", "falling idle").map { Animation(it, emptyList(), 1f, "$it.fbx") }
        val animator = Animator(animations = (stubs + loaded).toMutableList()).apply { controllerPath = Skater.CONTROLLER_PATH }
        assertTrue(animator.resolveController(LoggerService(), skeleton()))
        val ride = animator.stateMachine!!.motionOf("ride")
        assertTrue(ride is FittedMotion && ride.inner is ClipMotion, "ride plays $ride")
        assertEquals("skate riding", ((ride as FittedMotion).inner as ClipMotion).name)
    }

    private companion object {
        const val JAMES = "assets/characters/james.glb"
        const val RIDING = "assets/characters/animations/skate riding.fbx"
    }
}
