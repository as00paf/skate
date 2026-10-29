package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.prefab.PrefabInstance
import com.pafoid.skate.engine.ecs.prefab.PrefabOverrideKey
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PrefabStoreTest {
    @TempDir
    lateinit var dir: File

    private val serializer = Serializer()
    private val service = PrefabService(serializer)
    private val store = PrefabStore(serializer)

    private fun source(scaleX: Float = 1f) = GameObject("Rail").also {
        it.addComponent(Transform(scale = Vector3f(scaleX, 1f, 1f)))
        it.addChild(GameObject("Post").addComponent(Transform(translation = Vector3f(0f, 1f, 0f))))
    }

    @Test
    fun PrefabStore_TemplateRoundTripsThroughFile() {
        val asset = service.createAsset("rail", source(2f))
        val file = File(dir, "prefabs/rail.prefab")
        store.save(asset, file)
        val loaded = store.load(file)
        assertEquals("rail", loaded.id)
        assertEquals(asset.template, loaded.template)
        assertTrue(file.readText().contains("\"schema\""))
    }

    @Test
    fun PrefabStore_InstancesWithOverridesSurviveSceneAndPrefabReload() {
        val asset = service.createAsset("rail", source(1f))
        store.save(asset, File(dir, "rail.prefab"))

        val scene = Scene("S")
        val inst = service.instantiate(asset)
        inst.getComponent<Transform>()!!.translation.set(5f, 0f, 0f)
        service.captureOverrides(inst, asset)
        scene.addChild(inst)
        val sceneJson = serializer.encodeFile(FileKind.SCENE, scene)

        // "project reload": fresh serializer, prefab library and scene
        val ser2 = Serializer()
        val library = PrefabStore(ser2).loadAll(dir)
        val svc2 = PrefabService(ser2)
        val reloaded = ser2.decodeFile<Scene>(FileKind.SCENE, sceneJson).children.single()
        val marker = reloaded.components.filterIsInstance<PrefabInstance>().single()
        assertEquals(1, marker.overrides.size)
        val key = PrefabOverrideKey.of("", "Transform#0", "translation")
        assertTrue(key in marker.overrides)

        // edit the prefab on disk, then update the instance from the reloaded library
        PrefabStore(ser2).save(svc2.createAsset("rail", source(3f)), File(dir, "rail.prefab"))
        svc2.update(reloaded, PrefabStore(ser2).loadAll(dir).getValue(marker.prefabId))
        val t = reloaded.getComponent<Transform>()!!
        assertEquals(3f, t.scale.x)
        assertEquals(5f, t.translation.x)
        assertEquals(setOf("rail"), library.keys)
    }

    @Test
    fun PrefabStore_LoadAllReportsBadFilesAndKeepsGoodOnes() {
        store.save(service.createAsset("ok", source()), File(dir, "ok.prefab"))
        File(dir, "bad.prefab").writeText("not json")
        val bad = mutableListOf<String>()
        val all = store.loadAll(dir) { f, _ -> bad += f.name }
        assertEquals(setOf("ok"), all.keys)
        assertEquals(listOf("bad.prefab"), bad)
    }
}
