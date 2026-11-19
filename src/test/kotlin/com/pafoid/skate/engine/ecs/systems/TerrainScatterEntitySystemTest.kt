package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainPreset
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ECS-19: prefab scatter rules spawn capped, unsaved, hidden children that follow chunk changes. */
class TerrainScatterEntitySystemTest {

    private val prefabPath = "Prefabs/crate.prefab"

    private class FakeResolver(private val result: () -> GameObject? = {
        GameObject("Crate").addComponent(Transform(scale = Vector3f(2f)))
    }) : ScatterPrefabResolver {
        var calls = 0
        override fun instantiate(prefab: String): GameObject? { calls++; return result() }
    }

    private fun rule(maxEntities: Int, prefab: String = prefabPath, model: String = "") = ScatterRule(
        id = "crates", prefab = prefab, model = model, densityPer100m2 = 50f, minSpacing = 2f,
        maxEntities = maxEntities, avoidStamps = false,
    )

    private inner class Rig(maxEntities: Int, spawnsPerFrame: Int = 1000, resolver: FakeResolver = FakeResolver()) {
        val scene = Scene("scatter")
        val terrain = TerrainComponent(sizeMetres = 64f, sampleSpacing = 1f, chunkQuads = 16).apply {
            applyPreset(TerrainPreset.FLAT)
            biomes.clear()
            biomes.add(Biome(name = "Yard", scatter = listOf(rule(maxEntities))))
        }
        val ground = GameObject("Ground").addComponent(Transform()).addComponent(terrain)
        val resolver = resolver
        val system = TerrainScatterEntitySystem(resolver, spawnsPerFrame)

        init {
            scene.addChild(ground)
            build()
            system.init(scene)
        }

        fun build() = terrain.runtime.ensureBuilt(terrain.key(7L), parallel = false)

        fun setRule(rule: ScatterRule) {
            terrain.biomes[0] = terrain.biomes[0].copy(scatter = listOf(rule))
            build()
        }

        /** Every placed item of the rule, before the cap. */
        val available get() = terrain.runtime.chunks.sumOf { it.scatter.size }

        val spawned get() = system.spawned(terrain)
    }

    @Test
    fun Spawn_IsCappedPerRule_AsHiddenRuntimeOnlyChildren() {
        val rig = Rig(maxEntities = 10)
        assertTrue(rig.available > 10, "the test terrain must place more items than the cap, placed ${rig.available}")

        rig.system.update(0f)

        assertEquals(10, rig.spawned.size)
        assertTrue(rig.spawned.all { it.isRuntimeOnly && it.parent === rig.ground })
        assertTrue(rig.ground.persistentChildren.isEmpty(), "the hierarchy must not show scatter entities")
    }

    @Test
    fun Spawn_PlacesEachEntityOnItsItem_WithThePrefabScaleKept() {
        val rig = Rig(maxEntities = 5)
        rig.system.update(0f)

        val wanted = rig.system.wanted(rig.terrain).values.toList()
        rig.spawned.zip(wanted).forEach { (go, item) ->
            val t = go.getComponent<Transform>()!!
            assertEquals(Vector3f(item.x, item.y, item.z), t.translation)
            assertEquals(2f * item.scale, t.scale.x, 1e-5f)
            // Upright (alignToNormal 0): the Euler angles, applied as TransformSystem does, are a pure yaw.
            val r = t.rotation
            val actual = org.joml.Matrix4f()
                .rotate(Math.toRadians(r.x.toDouble()).toFloat(), Vector3f(1f, 0f, 0f))
                .rotate(Math.toRadians(r.y.toDouble()).toFloat(), Vector3f(0f, 1f, 0f))
                .rotate(Math.toRadians(r.z.toDouble()).toFloat(), Vector3f(0f, 0f, 1f))
            assertTrue(actual.equals(org.joml.Matrix4f().rotationY(item.yaw), 1e-4f), "rotation for yaw ${item.yaw}: $r")
        }
    }

    @Test
    fun Cap_ChoosesTheSameItemsEveryTime() {
        val a = Rig(maxEntities = 7).also { it.system.update(0f) }
        val b = Rig(maxEntities = 7).also { it.system.update(0f) }

        fun positions(r: Rig) = r.spawned.map { Vector3f(it.getComponent<Transform>()!!.translation) }.toSet()
        assertEquals(positions(a), positions(b))
    }

    @Test
    fun Save_LeavesScatterEntitiesOut() {
        val rig = Rig(maxEntities = 10)
        rig.ground.addChild(GameObject("Sign").addComponent(Transform()))
        rig.system.update(0f)
        val serializer = Serializer()

        val text = serializer.encodeFile(FileKind.SCENE, rig.scene)
        val reloaded = serializer.decodeFile<Scene>(FileKind.SCENE, text)

        assertFalse(text.contains("\"Crate\""))
        assertEquals(listOf("Sign"), reloaded.children.single().children.map { it.name })
        assertEquals(11, rig.ground.children.size, "saving must not remove the live entities")
    }

    @Test
    fun ChunkChange_KeepsStillWantedEntities_AndDespawnsTheRest() {
        val rig = Rig(maxEntities = 10)
        rig.system.update(0f)
        val before = rig.spawned.toSet()

        rig.setRule(rule(maxEntities = 4))
        rig.system.update(0f)

        assertEquals(4, rig.spawned.size)
        assertTrue(before.containsAll(rig.spawned), "kept items must keep their entity (and state)")
        assertEquals(4, rig.ground.children.size)
        assertTrue((before - rig.spawned.toSet()).all { it.isDead && it.parent == null })
    }

    @Test
    fun ChunkEdit_WithTheSameItems_DoesNotRespawn() {
        val rig = Rig(maxEntities = 10)
        rig.system.update(0f)
        val before = rig.spawned
        val calls = rig.resolver.calls

        rig.terrain.runtime.markEdited()
        rig.system.update(0f)

        assertEquals(before, rig.spawned)
        assertEquals(calls, rig.resolver.calls)
    }

    @Test
    fun ChangingThePrefab_RespawnsTheItems() {
        val rig = Rig(maxEntities = 3)
        rig.system.update(0f)
        val before = rig.spawned.toSet()

        rig.setRule(rule(maxEntities = 3, prefab = "Prefabs/barrel.prefab"))
        rig.system.update(0f)

        assertEquals(3, rig.spawned.size)
        assertTrue(rig.spawned.none { it in before })
    }

    @Test
    fun SpawnBudget_SpreadsSpawnsOverFrames() {
        val rig = Rig(maxEntities = 10, spawnsPerFrame = 3)

        rig.system.update(0f)
        assertEquals(3, rig.spawned.size)
        repeat(3) { rig.system.update(0f) }
        assertEquals(10, rig.spawned.size)
    }

    @Test
    fun DisabledTerrain_DespawnsAll_AndReenablingSpawnsAgain() {
        val rig = Rig(maxEntities = 5)
        rig.system.update(0f)

        rig.terrain.enabled = false
        rig.system.update(0f)
        assertTrue(rig.spawned.isEmpty())
        assertTrue(rig.ground.children.isEmpty())

        rig.terrain.enabled = true
        rig.system.update(0f)
        assertEquals(5, rig.spawned.size)
    }

    @Test
    fun TakenItem_StaysGoneUntilItsChunkChanges() {
        val rig = Rig(maxEntities = 5)
        rig.system.update(0f)
        val taken = rig.spawned.first()

        rig.ground.removeChild(taken)
        rig.system.update(0f)
        assertEquals(4, rig.spawned.size)

        rig.setRule(rule(maxEntities = 5).copy(minSpacing = 2.5f))
        rig.system.update(0f)
        assertEquals(5, rig.spawned.size)
    }

    @Test
    fun ModelRules_AndUnresolvedPrefabs_SpawnNothing() {
        val model = Rig(maxEntities = 5).apply { setRule(rule(maxEntities = 5, prefab = "", model = "Models/rock.obj")) }
        model.system.update(0f)
        assertTrue(model.spawned.isEmpty())
        assertEquals(0, model.resolver.calls)

        val missing = Rig(maxEntities = 5, resolver = FakeResolver { null })
        repeat(3) { missing.system.update(0f) }
        assertTrue(missing.spawned.isEmpty())
        assertEquals(1, missing.resolver.calls, "an unresolved prefab is not retried every frame")
    }
}
