package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.EmissionShape
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.Transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ParticleSystemTest {

    private lateinit var scene: Scene
    private lateinit var system: ParticleSystem
    private lateinit var emitter: ParticleEmitter

    @BeforeEach
    fun setup() {
        scene = Scene("test")
        emitter = ParticleEmitter(emissionRate = 10f)
        scene.addChild(GameObject("fx").addComponent(Transform()).addComponent(emitter))
        system = ParticleSystem()
        system.init(scene)
    }

    @Test
    fun ParticleSystem_EditModeDoesNotSimulate() {
        system.update(1f)
        assertEquals(0, emitter.runtime.pool.count)
    }

    @Test
    fun ParticleSystem_RunningScenePlaysOnStartAndEmits() {
        scene.isRunning = true
        system.update(1f)
        assertEquals(10, emitter.runtime.pool.count)
    }

    @Test
    fun ParticleSystem_LeavingPlayModeClearsParticles() {
        scene.isRunning = true
        system.update(1f)
        scene.isRunning = false
        system.update(0.1f)
        assertEquals(0, emitter.runtime.pool.count)
        assertTrue(!emitter.runtime.started)
    }

    @Test
    fun ParticleSystem_DisabledEmitterClearsParticles() {
        scene.isRunning = true
        system.update(1f)
        emitter.enabled = false
        system.update(0.1f)
        assertEquals(0, emitter.runtime.pool.count)
    }

    @Test
    fun ParticleEmitter_RoundTripsThroughPolymorphicComponentJson() {
        val serializer = Serializer()
        val original = ParticleEmitter(shape = EmissionShape.SPHERE, emissionRate = 3f, texturePath = "fx/smoke.png", atlasColumns = 4)
        original.startColor.set(0.1f, 0.2f, 0.3f, 0.4f)
        original.gravity.set(0f, -2f, 0f)

        val decoded = serializer.decode<Component>(serializer.encode<Component>(original)) as ParticleEmitter

        assertEquals(EmissionShape.SPHERE, decoded.shape)
        assertEquals(3f, decoded.emissionRate)
        assertEquals("fx/smoke.png", decoded.texturePath)
        assertEquals(4, decoded.atlasColumns)
        assertEquals(original.startColor, decoded.startColor)
        assertEquals(original.gravity, decoded.gravity)
    }
}
