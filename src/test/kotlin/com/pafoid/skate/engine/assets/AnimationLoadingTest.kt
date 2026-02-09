package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.VAOLoader
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AnimationLoadingTest {

    @Test
    fun `loadAnimations should load animations from FBX file`() {
        val loader = AssimpLoader()
        val path = "assets/characters/animations/idle.fbx"
        
        if (!File(path).exists()) {
            println("Skipping test: File not found $path")
            return
        }

        val animations = loader.loadAnimations(path)
        
        assertNotNull(animations)
        assertTrue(animations.isNotEmpty(), "Animations list should not be empty")
        
        val firstAnim = animations[0]
        assertTrue(firstAnim.duration > 0f, "Animation duration should be greater than 0")
        assertTrue(firstAnim.channels.isNotEmpty(), "Animation should have channels")
    }

    @Test
    fun `ResourceManager should load animations from file`() = runBlocking {
        val vaoLoader = mockk<VAOLoader>()
        val logger = mockk<LoggerService>(relaxed = true)
        val resourceManager = ResourceManager(vaoLoader = vaoLoader, logger = logger)
        val path = "assets/characters/animations/idle.fbx"

        if (!File(path).exists()) {
            println("Skipping test: File not found $path")
            return@runBlocking
        }

        val animation = resourceManager.loadAnimation(path)

        assertNotNull(animation)

        val retrieved = resourceManager.getAnimation(path)
        assertNotNull(retrieved, "Should be able to retrieve loaded animation by path")
    }

    @Test
    fun `ResourceManager should load animations synchronously from file`() {
        val vaoLoader = mockk<VAOLoader>()
        val logger = mockk<LoggerService>(relaxed = true)
        val resourceManager = ResourceManager(vaoLoader = vaoLoader, logger = logger)
        val path = "assets/characters/animations/idle.fbx"

        if (!File(path).exists()) {
            println("Skipping test: File not found $path")
            return
        }

        val animation = resourceManager.loadAnimationSync(path)

        assertNotNull(animation)

        val retrieved = resourceManager.getAnimation(path)
        assertNotNull(retrieved, "Should be able to retrieve loaded animation by path")
    }
}
