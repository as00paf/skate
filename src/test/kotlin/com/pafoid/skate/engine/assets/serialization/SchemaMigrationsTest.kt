package com.pafoid.skate.engine.assets.serialization

import com.pafoid.skate.engine.ecs.Scene
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SchemaMigrationsTest {
    private val serializer = Serializer()

    @Test
    fun Migration_UnversionedSceneUpgradesToCurrent() {
        val legacy = serializer.encode(Scene("Old"))
        assertFalse(legacy.contains("\"schema\""))
        val scene = serializer.decodeFile<Scene>(FileKind.SCENE, legacy)
        assertEquals("Old", scene.name)
    }

    @Test
    fun Migration_V0ToV1_StampsSchemaWithoutChangingContent() {
        val root = buildJsonObject { put("a", 1) }
        val out = SchemaMigrations().upgrade(FileKind.PROJECT, root)
        assertEquals(1, out["schema"]!!.jsonPrimitive.int)
        assertEquals(1, out["a"]!!.jsonPrimitive.int)
    }

    @Test
    fun Migration_ChainRunsEachStepInOrder() {
        val m = SchemaMigrations(
            mapOf(FileKind.PREFAB to mapOf(
                0 to Migration { JsonObject(it + ("v1" to JsonPrimitive(true))) },
                1 to Migration { JsonObject(it + ("v2" to JsonPrimitive(it.containsKey("v1")))) }
            ))
        )
        val out = m.upgrade(FileKind.PREFAB, JsonObject(emptyMap()), target = 2)
        assertEquals(2, m.schemaOf(out))
        assertEquals(true, out["v2"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun Migration_NewerThanSupportedIsRejected() {
        val root = buildJsonObject { put("schema", 99) }
        assertThrows(UnsupportedSchemaException::class.java) { SchemaMigrations().upgrade(FileKind.SCENE, root) }
    }

    @Test
    fun Migration_EncodeFileStampsSchemaAndRoundTrips() {
        val text = serializer.encodeFile(FileKind.SCENE, Scene("S"))
        assertEquals(FileKind.SCENE.currentSchema, serializer.json.parseToJsonElement(text).jsonObject["schema"]!!.jsonPrimitive.int)
        assertEquals("S", serializer.decodeFile<Scene>(FileKind.SCENE, text).name)
    }
}
