package com.pafoid.skate.engine.ecs.prefab

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrefabServiceTest {
    private val service = PrefabService(Serializer())

    private fun source(scaleX: Float = 1f): GameObject {
        val root = GameObject("Rail")
        root.addComponent(Transform(scale = org.joml.Vector3f(scaleX, 1f, 1f)))
        val child = GameObject("Post")
        child.addComponent(Transform(translation = org.joml.Vector3f(0f, 1f, 0f)))
        root.addChild(child)
        return root
    }

    private val scaleKey = PrefabOverrideKey.of("", "Transform#0", "scale")

    @Test
    fun Prefab_InstantiateCopiesTemplateWithFreshIdsAndMarker() {
        val asset = service.createAsset("rail", source())
        val a = service.instantiate(asset)
        val b = service.instantiate(asset)
        assertTrue(a.uId != b.uId)
        assertEquals("rail", a.components.filterIsInstance<PrefabInstance>().single().prefabId)
        assertEquals("Post", a.children.single().name)
        assertEquals(a, a.children.single().parent)
    }

    @Test
    fun Prefab_EditUpdatesInstanceButKeepsOverrides() {
        val asset = service.createAsset("rail", source(scaleX = 1f))
        val inst = service.instantiate(asset)
        inst.getComponent<Transform>()!!.translation.set(5f, 0f, 0f)
        val captured = service.captureOverrides(inst, asset)
        assertTrue(PrefabOverrideKey.of("", "Transform#0", "translation") in captured)
        assertEquals(1, captured.size)

        // prefab edited: scale changes, child moves
        val edited = source(scaleX = 3f)
        edited.children.single().getComponent<Transform>()!!.translation.set(0f, 2f, 0f)
        val newAsset = service.createAsset("rail", edited)
        val result = service.update(inst, newAsset)

        assertTrue(result.droppedOverrides.isEmpty())
        val t = inst.getComponent<Transform>()!!
        assertEquals(3f, t.scale.x)
        assertEquals(5f, t.translation.x)
        assertEquals(2f, inst.children.single().getComponent<Transform>()!!.translation.y)
    }

    @Test
    fun Prefab_OverrideOfScaleWinsOverPrefabEditAndRevertRestoresPrefabValue() {
        val asset = service.createAsset("rail", source(1f))
        val inst = service.instantiate(asset)
        inst.getComponent<Transform>()!!.scale.set(9f, 1f, 1f)
        service.captureOverrides(inst, asset)

        val newAsset = service.createAsset("rail", source(2f))
        service.update(inst, newAsset)
        assertEquals(9f, inst.getComponent<Transform>()!!.scale.x)

        service.revertOverride(inst, scaleKey)
        service.update(inst, newAsset)
        assertEquals(2f, inst.getComponent<Transform>()!!.scale.x)
    }

    @Test
    fun Prefab_UpdateKeepsIdentityParentAndUid() {
        val asset = service.createAsset("rail", source())
        val parent = GameObject("root")
        val inst = service.instantiate(asset)
        parent.addChild(GameObject("before"))
        parent.addChild(inst)
        val uid = inst.uId
        service.update(inst, service.createAsset("rail", source(4f)))
        assertEquals(parent, inst.parent)
        assertEquals(uid, inst.uId)
        assertNotNull(inst.components.filterIsInstance<PrefabInstance>().singleOrNull())
    }

    @Test
    fun Prefab_OverridesForRemovedNodesAreDropped() {
        val asset = service.createAsset("rail", source())
        val inst = service.instantiate(asset)
        val key = PrefabOverrideKey.of("Post#0", PrefabOverrideKey.OBJECT, "name")
        service.setOverride(inst, key, JsonPrimitive("Renamed"))
        service.update(inst, asset)
        assertEquals("Renamed", inst.children.single().name)

        val noChild = GameObject("Rail").also { it.addComponent(Transform()) }
        val result = service.update(inst, service.createAsset("rail", noChild))
        assertEquals(listOf(key), result.droppedOverrides)
        assertTrue(inst.children.isEmpty())
    }

    @Test
    fun Prefab_InstanceSurvivesSerializationRoundTrip() {
        val ser = Serializer()
        val asset = service.createAsset("rail", source())
        val inst = service.instantiate(asset, mapOf(scaleKey to ser.json.parseToJsonElement("{\"x\":7.0,\"y\":1.0,\"z\":1.0}")))
        val copy = ser.decode<GameObject>(ser.encode(inst))
        assertEquals("rail", copy.components.filterIsInstance<PrefabInstance>().single().prefabId)
        assertEquals(1, copy.components.filterIsInstance<PrefabInstance>().single().overrides.size)
    }
}
