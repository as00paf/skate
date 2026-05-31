package com.pafoid.skate.engine.ecs

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SceneTraversalTest {

    @Test
    fun `collectGameObjectsDepthFirst traverses nested hierarchy`() {
        val scene = mockk<Scene>(relaxed = true)
        val rootA = GameObject("RootA")
        val childA = GameObject("ChildA")
        val grandChildA = GameObject("GrandChildA")
        val rootB = GameObject("RootB")

        childA.addChild(grandChildA)
        rootA.addChild(childA)
        every { scene.gameObjects } returns mutableListOf(rootA, rootB)

        val names = scene.collectGameObjectsDepthFirst().map { it.name }

        assertEquals(listOf("RootA", "ChildA", "GrandChildA", "RootB"), names)
    }
}
