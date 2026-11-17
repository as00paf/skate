package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.assets.data.models.animations.BoneMirrorUtil
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-15 acceptance: the cleanup tools on a real Mixamo mocap clip (walking.fbx) on the skater rig (james.glb). */
class CleanupSkaterTest {
    private val skeleton = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!!
    private val rig = ClipRig(skeleton)
    private val walk = SkAnimFormat.fromAnimation(AnimationLoader().loadAnimations(WALKING)[0])

    private fun globals(c: SkClip, t: Float): List<Vector3f> {
        val locals = ClipEvaluator(c, rig).locals(t)
        return (0 until rig.boneCount).map { IkSolvers.globalOf(rig.parents, locals, it).getTranslation(Vector3f()) }
    }

    /** Size of the rig, so tolerances hold whatever unit the file uses. */
    private val height: Float = globals(walk, 0f).let { p -> p.maxOf { it.y } - p.minOf { it.y } }

    @Test
    fun `Mirror_ReflectsTheRealWalkOntoTheOtherSide`() {
        val mirrored = ClipMirror.apply(walk, skeleton)
        val twin = IntArray(rig.boneCount) { i -> rig.names.indexOf(BoneMirrorUtil.getMirroredBoneName(rig.names[i])).takeIf { it >= 0 } ?: i }
        assertTrue(twin.withIndex().count { (i, m) -> i != m } >= 10, "the rig has left/right pairs")
        // James has left fingertip end bones with no right twin; they follow their mirrored parent instead.
        val unpaired = (0 until rig.boneCount).filter { twin[it] == it && BoneMirrorUtil.getMirroredBoneName(rig.names[it]) != rig.names[it] }.toSet()
        // The rig itself is a few millimetres asymmetric at rest (the mirror keeps its rest pose). Each joint lands on
        // its twin's reflection up to the asymmetry of its chain: an ancestor's offset turns with the motion. A wrong
        // mirror would be off by tens of centimetres.
        val rest = (0 until rig.boneCount).map { IkSolvers.globalOf(rig.parents, rig.bindLocals, it).getTranslation(Vector3f()) }
        fun own(b: Int) = rest[b].distance(Vector3f(rest[twin[b]]).mul(-1f, 1f, 1f))
        fun restAsymmetry(b: Int): Float = generateSequence(b) { rig.parents[it].takeIf { p -> p >= 0 } }.sumOf { own(it).toDouble() }.toFloat() * 2f
        for (t in listOf(0.1f, 0.45f, walk.duration * 0.5f)) {
            val before = globals(walk, t)
            val after = globals(mirrored, t)
            for (b in 0 until rig.boneCount) {
                if (b in unpaired) continue
                val expected = Vector3f(before[twin[b]]).mul(-1f, 1f, 1f)
                val d = after[b].distance(expected)
                assertTrue(d < restAsymmetry(b) + height * 1e-4f, "${rig.names[b]} at $t: off by $d, rest asymmetry ${restAsymmetry(b)}")
            }
        }
        SkAnimFormat.validate(mirrored)
    }

    @Test
    fun `FootLock_PlantsTheSkatersFootOnTheRealWalk`() {
        val leg = LegChain.detect(rig.names.asList(), left = true)
        assertNotNull(leg, "the skater rig has a left leg")
        leg!!
        val foot = rig.indexOf(leg.foot)
        val locked = FootLock.apply(walk, skeleton, leg, 0.3f, 0.5f, blendSeconds = 0.1f)
        val planted = globals(locked, 0.3f)[foot]
        for (t in listOf(0.35f, 0.4f, 0.5f)) {
            val d = globals(locked, t)[foot].distance(planted)
            assertTrue(d < height * 0.005f, "foot moved ${d / height * 100f}% of the body height at $t")
        }
    }

    @Test
    fun `Reduce_KeepsTheRealWalkWithinTolerance`() {
        val tolerance = ReductionTolerance(translationMetres = height * 0.002f, rotationDeg = 0.5f)
        val reduced = Reduction.reduce(walk, tolerance)
        val keysBefore = walk.tracks.sumOf { it.keys.size }
        val keysAfter = reduced.tracks.sumOf { it.keys.size }
        assertTrue(keysAfter < keysBefore * 0.7f, "reduced $keysBefore -> $keysAfter keys")
        for ((a, b) in walk.tracks.zip(reduced.tracks)) for (k in a.keys) {
            val e = Reduction.error(a.path, k.value.toFloatArray(), KeyEditing.sample(b, k.time))
            assertTrue(e <= tolerance.of(a.path) * 1.001f, "${a.bone} ${a.path} at ${k.time}: error $e")
        }
    }

    @Test
    fun `TrimSeamAndResample_KeepTheRealWalkPlayable`() {
        val trimmed = ClipEditing.trim(walk, 0.2f, walk.duration - 0.2f)
        assertEquals(walk.duration - 0.4f, trimmed.duration, 1e-4f)
        val a = globals(walk, 0.5f); val b = globals(trimmed, 0.3f)
        for (i in a.indices) assertTrue(a[i].distance(b[i]) < height * 1e-3f, "trim keeps the motion (${rig.names[i]})")

        val looped = LoopSeam.apply(trimmed, 0.25f)
        val start = globals(looped, 0f); val end = globals(looped, looped.duration)
        for (i in start.indices) assertTrue(start[i].distance(end[i]) < height * 0.01f, "seam closes (${rig.names[i]})")

        val resampled = Filters.resample(looped, 15f)
        SkAnimFormat.validate(resampled)
        assertEquals(15f, resampled.fps)
    }

    private companion object {
        const val JAMES = "assets/Models/james.glb"
        const val WALKING = "assets/Animations/walking.fbx"
    }
}
