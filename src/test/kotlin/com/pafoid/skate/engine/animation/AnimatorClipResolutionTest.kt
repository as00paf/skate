package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.utils.AssetsResolver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** BUG-021 / ANI-11: clips are re-resolved from disk (absolute or project-relative) and from an exported game's pack. */
class AnimatorClipResolutionTest {
    private val logger = LoggerService()

    private fun assets(paths: AssetPaths) = mockk<AssetsManager>().also {
        every { it.paths } returns paths
        every { it.loadAnimationSync(any()) } answers { Animation(File(firstArg<String>()).nameWithoutExtension, emptyList(), 1f, firstArg()) }
    }

    private fun animator(vararg paths: String) =
        Animator(animations = paths.map { Animation(File(it).nameWithoutExtension, emptyList(), 1f, it) }.toMutableList())

    @Test
    fun `AnimatorClips_ResolveFromDiskAbsoluteAndProjectRelative`(@TempDir dir: File) {
        val absolute = File(dir, "Assets/walk.fbx").apply { parentFile.mkdirs(); writeText("x") }.absolutePath
        File(dir, "Assets/run.fbx").writeText("x")
        val animator = animator(absolute, "Assets/run.fbx", "Assets/missing.fbx")

        animator.resolveAnimationsFromPaths(assets(AssetPaths().apply { projectRoot = dir }), logger)

        assertEquals(listOf("walk", "run"), animator.animations.map { it.name })
        assertEquals("walk", animator.currentAnimation?.name)
        assertTrue(logger.logs.any { it.level == LogLevel.WARN && "missing.fbx" in it.message })
        assertTrue(logger.logs.none { it.level == LogLevel.ERROR })
    }

    @Test
    fun `AnimatorClips_ResolveFromThePackInAnExportedGame`() {
        val packed = "EngineDefaults/Characters/animations/idle_0.fbx"
        val pack = mockk<AssetsResolver>(relaxed = true).also {
            every { it.initialized } returns true
            every { it.find(any()) } answers { if (firstArg<String>() == packed) mockk() else null }
            every { it.resolveBytes(packed) } returns byteArrayOf(1, 2, 3)
        }
        val animator = animator(packed, "EngineDefaults/Characters/animations/gone.fbx")

        animator.resolveAnimationsFromPaths(assets(AssetPaths().apply { this.pack = pack }), logger)

        assertEquals(listOf("idle_0"), animator.animations.map { it.name })
    }
}
