package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.terrain.TerrainPreset
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-26: PhysicsSystem keeps chunk colliders for scene terrains, in edit mode and in Play. */
class PhysicsSystemTerrainTest {

    @Test
    fun sceneTerrain_GetsChunkColliders_AndACrateLandsOnIt() {
        NativeLibraryLoader().loadNativeLibrary()
        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))
        val manager = SystemManager().apply { addSystem(physics); start() }

        val terrain = TerrainComponent(sizeMetres = 64f, sampleSpacing = 2f, heightScaleMetres = 16f, chunkQuads = 16).apply {
            applyPreset(TerrainPreset.ROLLING_HILLS)
            runtime.ensureBuilt(key(projectSeed = 3L), parallel = false)
        }
        val ground = GameObject("Terrain").apply {
            addComponent(Transform(Vector3f(0f, -4f, 0f)).also { it.worldMatrix.translation(0f, -4f, 0f) })
            addComponent(terrain)
        }
        val field = terrain.runtime.heightField!!
        val crate = GameObject("Crate").apply {
            addComponent(Transform(Vector3f(10f, field.heightAt(10f, -10f), -10f)))
            addComponent(RigidBody3D(10f))
            addComponent(BoxCollider3D(Vector3f(0.5f)))
        }
        val scene = Scene().apply {
            addComponent(ScenePhysicsComponent())
            addChild(ground)
            addChild(crate)
        }
        manager.loadScene(scene)

        // Edit mode: built on the first frame
        manager.update(1f / 60f)
        physics.onFrame(1f / 60f, 0f)
        assertEquals(4, physics.terrainColliderBodyCount)

        scene.isRunning = true
        repeat(180) { manager.update(1f / 60f) }

        val at = crate.getComponent<RigidBody3D>()!!.getWorldPosition()
        val surface = -4f + field.heightAt(at.x, at.z)
        assertTrue(at.y > surface && at.y < surface + 1.2f, "crate at $at, terrain surface $surface")

        terrain.collider = false
        manager.update(1f / 60f)
        assertEquals(0, physics.terrainColliderBodyCount)
    }
}
