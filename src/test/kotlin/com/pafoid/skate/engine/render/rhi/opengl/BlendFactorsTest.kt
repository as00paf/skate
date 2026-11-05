package com.pafoid.skate.engine.render.rhi.opengl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_ZERO

/**
 * REN-21: the tracker used to assume (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) without asking GL, so on a context still at the
 * GL default (ONE, ZERO) the UI's setBlendFunc was skipped and UI quads overwrote the target, alpha included.
 */
class BlendFactorsTest {

    @Test
    fun cacheSyncedFromGlDefault_DoesNotMatchStraightAlpha() {
        val cache = IntArray(4)
        BlendFactors.store(cache, GL_ONE, GL_ZERO, GL_ONE, GL_ZERO)

        assertFalse(BlendFactors.matches(cache, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA))
    }

    @Test
    fun alphaFactorsAreComparedSeparately() {
        val cache = IntArray(4)
        BlendFactors.store(cache, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        assertFalse(BlendFactors.matches(cache, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA))
        BlendFactors.store(cache, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        assertTrue(BlendFactors.matches(cache, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA))
    }
}
