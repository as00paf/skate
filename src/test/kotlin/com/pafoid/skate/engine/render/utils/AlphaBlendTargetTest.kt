package com.pafoid.skate.engine.render.utils

import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA

/**
 * REN-19: transparent materials blend with explicit factors that keep the scene target's alpha at 1, because the
 * editor viewport composites that texture with its alpha (post-processing off left it below 1).
 */
class AlphaBlendTargetTest {
    @AfterEach
    fun tearDown() = unmockkObject(GLStateTracker)

    @Test
    fun AlphaBlend_UsesSeparateAlphaFactorsThatKeepTheTargetOpaque() {
        mockkObject(GLStateTracker)
        every { GLStateTracker.setBlendFuncSeparate(any(), any(), any(), any()) } just runs

        setAlphaBlendKeepingTargetOpaque()

        verify { GLStateTracker.setBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA) }
    }

    /** Blend equation result alpha for one fragment of alpha [a] over an opaque destination. */
    private fun resultAlpha(src: Float, dst: Float, a: Float) = a * src + 1f * dst

    @Test
    fun AlphaBlend_MathKeepsAlphaOneWhereThePlainFuncDidNot() {
        for (a in listOf(0.1f, 0.35f, 0.9f)) {
            assertEquals(1f, resultAlpha(1f, 1f - a, a), 1e-6f, "(ONE, ONE_MINUS_SRC_ALPHA) at a=$a")
            assertTrue(resultAlpha(a, 1f - a, a) < 1f, "(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) at a=$a lowered the alpha")
        }
    }
}
