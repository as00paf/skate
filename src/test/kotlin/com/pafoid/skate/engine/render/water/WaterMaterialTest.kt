package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchemas
import com.pafoid.skate.engine.render.material.UniformSink
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** REN-29 §6.5: the "water" schema, its bundled presets and swapping looks by material. */
class WaterMaterialTest {

    private class RecordingSink : UniformSink {
        val values = LinkedHashMap<String, Any>()
        override fun setFloat(name: String, value: Float) { values[name] = value }
        override fun setInt(name: String, value: Int) { values[name] = value }
        override fun setBool(name: String, value: Boolean) { values[name] = value }
        override fun setVec2(name: String, x: Float, y: Float) { values[name] = listOf(x, y) }
        override fun setVec3(name: String, x: Float, y: Float, z: Float) { values[name] = listOf(x, y, z) }
        override fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) { values[name] = listOf(x, y, z, w) }
    }

    @Test
    fun Schema_RegistersAsWater() {
        WaterSchema.register()
        assertSame(WaterSchema.SCHEMA, MaterialSchemas.get("water"))
    }

    @Test
    fun Material_UsesDefaultsForMissingParams_AndAssetValuesOverThem() {
        val pool = WaterMaterial.from(WaterSchema.BUILT_IN.getValue(WaterMaterials.POOL)) { null }
        assertArrayEquals(floatArrayOf(6f), pool.param(WaterSchema.Param.ABSORPTION_DISTANCE))
        assertArrayEquals(floatArrayOf(0.03f), pool.param(WaterSchema.Param.REFRACTION_STRENGTH)) // default
    }

    @Test
    fun Material_RejectsOtherSchemasAndUnknownParams() {
        assertThrows<IllegalArgumentException> { WaterMaterial.from(MaterialAsset(schema = "pbr-metallic-roughness")) { null } }
        assertThrows<IllegalArgumentException> { WaterMaterial.from(MaterialAsset(schema = "water", params = mapOf("bogus" to listOf(1f)))) { null } }
        assertThrows<IllegalArgumentException> {
            WaterMaterial.from(MaterialAsset(schema = "water", params = mapOf(WaterSchema.Param.DEEP_COLOR to listOf(1f)))) { null }
        }
    }

    @Test
    fun Resolve_ProjectSkmatWins_ThenBuiltIns_ThenTheDefault() {
        val custom = MaterialAsset(name = "Murky", schema = "water", params = mapOf(WaterSchema.Param.ABSORPTION_DISTANCE to listOf(0.5f)))
        val files = mapOf("Assets/murky.skmat" to custom.toJson())
        val read = { path: String -> files[path] }
        assertEquals("Murky", WaterMaterial.resolve("Assets/murky.skmat", read) { null }.name)
        assertEquals("Water (ocean)", WaterMaterial.resolve(WaterMaterials.OCEAN, read) { null }.name)
        assertEquals("Water (lake)", WaterMaterial.resolve("Assets/missing.skmat", read) { null }.name)
        // A PBR material at that path is not water: fall back
        val pbr = mapOf("Assets/pbr.skmat" to MaterialAsset(name = "Brick").toJson())
        assertEquals("Water (lake)", WaterMaterial.resolve("Assets/pbr.skmat", { pbr[it] }) { null }.name)
    }

    @Test
    fun Upload_SetsEveryParam_SamplerUnitsAndTextureFlags() {
        val foam = mockk<Texture>(relaxed = true)
        val asset = MaterialAsset(schema = "water", textures = mapOf(WaterSchema.Tex.FOAM to "foam.png"))
        val material = WaterMaterial.from(asset) { path -> foam.takeIf { path == "foam.png" } }
        val sink = RecordingSink()
        material.upload(sink)
        for (p in WaterSchema.SCHEMA.params) assertTrue(p.uniform in sink.values, p.uniform)
        assertEquals(true, sink.values["uHasFoam"])
        assertEquals(false, sink.values["uHasNormalA"])
        assertEquals(14, sink.values["foam"])
        assertEquals(true, sink.values["uSsrEnabled"])
    }

    @Test
    fun Component_PresetsSetWavesFlowAndMaterial() {
        val water = WaterComponent()
        water.applyPreset(WaterPreset.RIVER)
        assertEquals(1f, water.flowVelocity.x)
        assertEquals(WaterMaterials.DEFAULT, water.material)
        water.applyPreset(WaterPreset.CALM_POOL)
        assertEquals(0f, water.flowVelocity.x)
        assertEquals(WaterMaterials.POOL, water.material)
        assertEquals(WaterPreset.CALM_POOL.waves(), water.waves)
        water.sizeX = 3f
        water.reset()
        assertEquals(20f, water.sizeX)
        assertEquals(WaterPreset.LAKE.waves(), water.waves)
    }
}
