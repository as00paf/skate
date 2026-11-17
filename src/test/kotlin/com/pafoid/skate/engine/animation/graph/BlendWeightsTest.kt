package com.pafoid.skate.engine.animation.graph

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** ANI-23: the blend weights the Animator window previews are the runtime's. */
class BlendWeightsTest {
    private val still = object : Motion {
        override fun duration(params: AnimParameters) = 1f
        override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) = Unit
    }

    @Test
    fun Blend1D_SharesBetweenTheNeighboursInChildOrder() {
        // Children listed out of order: weights still come back per child.
        val w = BlendWeights.blend1D(floatArrayOf(2f, 0f, 1f), 1.5f)
        assertArrayEquals(floatArrayOf(0.5f, 0f, 0.5f), w, 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), BlendWeights.blend1D(floatArrayOf(2f, 0f, 1f), -3f), 1e-6f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), BlendWeights.blend1D(floatArrayOf(2f, 0f, 1f), 9f), 1e-6f)
        assertArrayEquals(floatArrayOf(1f), BlendWeights.blend1D(floatArrayOf(4f), 0f), 1e-6f)
    }

    @Test
    fun Blend1D_MatchesTheRuntimeTree() {
        val thresholds = listOf(0f, 1f, 1f, 3f)
        val tree = BlendTree1D("speed", thresholds.map { it to still })
        for (v in listOf(-1f, 0f, 0.4f, 1f, 2.2f, 3f, 5f)) {
            val (lo, hi, upper) = tree.select(v)
            val expected = FloatArray(4).also { it[lo] += 1f - upper; it[hi] += upper }
            assertArrayEquals(expected, BlendWeights.blend1D(thresholds.toFloatArray(), v), 1e-6f, "value $v")
        }
    }

    @Test
    fun Blend2D_IsInverseDistanceAndExactOnAChild() {
        val xs = floatArrayOf(0f, 1f, 0f)
        val ys = floatArrayOf(0f, 0f, 1f)
        val tree = BlendTree2D("x", "y", xs.indices.map { Triple(xs[it], ys[it], still) })
        assertArrayEquals(tree.weights(0.3f, 0.6f), BlendWeights.blend2D(xs, ys, 0.3f, 0.6f), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), BlendWeights.blend2D(xs, ys, 1f, 0f), 1e-6f)
        assertEquals(1f, BlendWeights.blend2D(xs, ys, 5f, -2f).sum(), 1e-5f)
    }

    @Test
    fun Of_ReadsTheDefinition() {
        val oneD = Blend1DDef("speed", listOf(Blend1DChild(0f, ClipRef("walk")), Blend1DChild(2f, ClipRef("run"))))
        assertArrayEquals(floatArrayOf(0.75f, 0.25f), BlendWeights.of(oneD, 0.5f), 1e-6f)
        val twoD = Blend2DDef("x", "y", listOf(Blend2DChild(0f, 0f, ClipRef("a")), Blend2DChild(0f, 1f, ClipRef("b"))))
        assertArrayEquals(floatArrayOf(0f, 1f), BlendWeights.of(twoD, 0f, 1f), 1e-6f)
        assertNull(BlendWeights.of(ClipRef("a"), 0f))
    }

    @Test
    fun RenameMotion_StatesFollowAndClashesAreRefused() {
        val tree = Blend1DDef("speed", listOf(Blend1DChild(0f, ClipRef("walk"))))
        val asset = AnimationControllerAsset("c", "a", listOf(StateDef("a", "loco"), StateDef("b", "loco"), StateDef("c", "other")),
            motions = mapOf("loco" to tree, "other" to ClipRef("x")))
        val renamed = ControllerEditor(asset).renameMotion("loco", "locomotion")
        assertEquals(setOf("locomotion", "other"), renamed.motions.keys)
        assertEquals(listOf("locomotion", "locomotion", "other"), renamed.states.map { it.motion })
        assertThrows(IllegalArgumentException::class.java) { ControllerEditor(asset).renameMotion("loco", "other") }
        assertThrows(IllegalArgumentException::class.java) { ControllerEditor(asset).renameMotion("loco", " ") }
    }
}
