package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemManagerInvalidationTest {

    private class EligibilityComponent : Component()

    private class EligibilityCacheSystem : System() {
        private val cachedEligibleNames = mutableListOf<String>()
        private var cacheDirty = true
        var rebuildCount = 0
            private set

        val eligibleNames: List<String>
            get() = cachedEligibleNames

        override fun init(scene: Scene) {
            super.init(scene)
            rebuildCache()
            cacheDirty = false
        }

        override fun invalidateCaches() {
            cacheDirty = true
        }

        override fun update(dt: Float) {
            if (!cacheDirty) return
            rebuildCache()
            cacheDirty = false
        }

        private fun rebuildCache() {
            rebuildCount++
            cachedEligibleNames.clear()
            scene.gameObjects.forEach { gameObject ->
                if (gameObject.hasComponent<EligibilityComponent>()) {
                    cachedEligibleNames.add(gameObject.name)
                }
            }
        }
    }

    @Test
    fun `component add invalidates cached eligibility set`() {
        val gameObject = GameObject("Skater")
        val scene = createScene(mutableListOf(gameObject))

        val cacheSystem = EligibilityCacheSystem()
        val systemManager = SystemManager()
        systemManager.addSystem(cacheSystem)
        systemManager.loadScene(scene)

        systemManager.update(0.016f)
        assertFalse(cacheSystem.eligibleNames.contains("Skater"))

        gameObject.addComponent(EligibilityComponent())
        systemManager.update(0.016f)

        assertTrue(cacheSystem.eligibleNames.contains("Skater"))
        assertTrue(cacheSystem.rebuildCount >= 2)
    }

    @Test
    fun `component remove invalidates cached eligibility set`() {
        val gameObject = GameObject("Skater").addComponent(EligibilityComponent())
        val scene = createScene(mutableListOf(gameObject))

        val cacheSystem = EligibilityCacheSystem()
        val systemManager = SystemManager()
        systemManager.addSystem(cacheSystem)
        systemManager.loadScene(scene)
        systemManager.update(0.016f)
        assertTrue(cacheSystem.eligibleNames.contains("Skater"))

        gameObject.removeComponent<EligibilityComponent>()
        systemManager.update(0.016f)

        assertFalse(cacheSystem.eligibleNames.contains("Skater"))
        assertTrue(cacheSystem.rebuildCount >= 2)
    }

    @Test
    fun `component replace invalidates cache even when eligibility remains true`() {
        val gameObject = GameObject("Skater").addComponent(EligibilityComponent())
        val scene = createScene(mutableListOf(gameObject))

        val cacheSystem = EligibilityCacheSystem()
        val systemManager = SystemManager()
        systemManager.addSystem(cacheSystem)
        systemManager.loadScene(scene)
        systemManager.update(0.016f)

        val rebuildCountBeforeReplace = cacheSystem.rebuildCount
        gameObject.addComponent(EligibilityComponent())
        systemManager.update(0.016f)

        assertTrue(cacheSystem.eligibleNames.contains("Skater"))
        assertEquals(rebuildCountBeforeReplace + 1, cacheSystem.rebuildCount)
    }

    @Test
    fun `object-set version invalidation remains supported`() {
        val gameObject = GameObject("Skater").addComponent(EligibilityComponent())
        val gameObjects = mutableListOf(gameObject)
        var objectSetVersion = 0L
        val scene = mockk<Scene>(relaxed = true)
        every { scene.gameObjects } returns gameObjects
        every { scene.objectSetVersion } answers { objectSetVersion }
        every { scene.getComponentMutationVersion() } returns 0L

        val cacheSystem = EligibilityCacheSystem()
        val systemManager = SystemManager()
        systemManager.addSystem(cacheSystem)
        systemManager.loadScene(scene)
        systemManager.update(0.016f)

        val rebuildCountBeforeObjectSetChange = cacheSystem.rebuildCount
        objectSetVersion++
        systemManager.update(0.016f)

        assertEquals(rebuildCountBeforeObjectSetChange + 1, cacheSystem.rebuildCount)
    }

    private fun createScene(gameObjects: MutableList<GameObject>): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.gameObjects } returns gameObjects
        every { scene.objectSetVersion } returns 0L
        every { scene.getComponentMutationVersion() } returns 0L
        return scene
    }
}
