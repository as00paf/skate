package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MaterialSystemTest {
    private class RecordingSink : UniformSink {
        val calls = mutableListOf<String>()
        override fun setFloat(name: String, value: Float) { calls += "$name=$value" }
        override fun setInt(name: String, value: Int) { calls += "$name=$value" }
        override fun setBool(name: String, value: Boolean) { calls += "$name=$value" }
        override fun setVec2(name: String, x: Float, y: Float) { calls += "$name=$x,$y" }
        override fun setVec3(name: String, x: Float, y: Float, z: Float) { calls += "$name=$x,$y,$z" }
        override fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) { calls += "$name=$x,$y,$z,$w" }
    }

    private val binds = mutableListOf<Pair<Int, Int>>()
    private fun binder() = MaterialBinder { slot, id -> binds += slot to id }

    private fun tex(id: Int, path: String? = null) = Texture(filePath = path).also { it.texId = id }

    @Test
    fun MaterialSchema_RejectsDefaultWithWrongComponentCount() {
        assertThrows(IllegalArgumentException::class.java) {
            MaterialParamDef("x", ParamType.VEC3, "u", listOf(1f))
        }
    }

    @Test
    fun MaterialBinder_UploadsEveryParamAndSamplerWithHasFlags() {
        val sink = RecordingSink()
        val m = Material(baseColorTexture = tex(7), metallicFactor = 0.8f, baseColor = Vector4f(1f, 0f, 0f, 1f))
        binder().bind(m, sink)
        assertTrue("${Uniforms.METALLIC_FACTOR}=0.8" in sink.calls)
        assertTrue("${Uniforms.BASE_COLOR}=1.0,0.0,0.0,1.0" in sink.calls)
        assertTrue("${Uniforms.HAS_BASE_COLOR_TEXTURE}=true" in sink.calls)
        assertTrue("${Uniforms.HAS_NORMAL_MAP}=false" in sink.calls)
        assertTrue("${Uniforms.BASE_COLOR_TEXTURE}=0" in sink.calls)
        assertEquals(listOf(0 to 7), binds)
    }

    @Test
    fun MaterialBinder_SameMaterialSameShaderSkipsUploads() {
        val sink = RecordingSink()
        val b = binder()
        val m = Material()
        b.bind(m, sink)
        val n = sink.calls.size
        b.bind(m, sink)
        assertEquals(n, sink.calls.size)
        assertEquals(1L, b.uploadsSkipped)
    }

    @Test
    fun MaterialBinder_ChangedValueOrOtherShaderReuploads() {
        val a = RecordingSink()
        val b2 = RecordingSink()
        val b = binder()
        val m = Material()
        b.bind(m, a)
        m.roughnessFactor = 0.9f
        b.bind(m, a)
        assertTrue("${Uniforms.ROUGHNESS_FACTOR}=0.9" in a.calls)
        b.bind(m, b2)
        assertTrue(b2.calls.isNotEmpty())
        assertEquals(0L, b.uploadsSkipped)
    }

    @Test
    fun MaterialBinder_InvalidateForcesUpload() {
        val sink = RecordingSink()
        val b = binder()
        val m = Material()
        b.bind(m, sink)
        b.invalidate()
        b.bind(m, sink)
        assertEquals(2L, b.uploadsIssued)
    }

    @Test
    fun MaterialAsset_RoundTripStoresOnlyOverridesAndTexturePaths() {
        val m = Material(baseColorTexture = tex(3, "a/b.png"), metallicFactor = 0.4f, doubleSided = true, alphaMode = AlphaMode.MASK)
        val asset = MaterialAsset.from(m, "Wood")
        assertEquals(setOf("metallicFactor"), asset.params.keys)
        val back = MaterialAsset.fromJson(asset.toJson()).toMaterial { tex(9, it) }
        assertEquals(0.4f, back.metallicFactor)
        assertEquals(0.5f, back.roughnessFactor)
        assertTrue(back.doubleSided)
        assertEquals(AlphaMode.MASK, back.alphaMode)
        assertEquals("a/b.png", back.baseColorTexture?.filePath)
        assertNull(back.normalMap)
    }

    @Test
    fun MaterialAsset_RejectsUnknownParamUnknownSchemaAndNewerVersion() {
        assertThrows(IllegalArgumentException::class.java) { MaterialAsset(params = mapOf("nope" to listOf(1f))).toMaterial { null } }
        assertThrows(IllegalArgumentException::class.java) { MaterialAsset(params = mapOf("metallicFactor" to listOf(1f, 2f))).toMaterial { null } }
        assertThrows(IllegalArgumentException::class.java) { MaterialAsset(schema = "x").toMaterial { null } }
        assertThrows(IllegalArgumentException::class.java) {
            MaterialAsset.fromJson(MaterialAsset(version = MaterialAsset.CURRENT_VERSION + 1).toJson())
        }
    }

    @Test
    fun Material_EachInstanceHasUniqueSortId() {
        assertNotEquals(Material().id, Material().id)
    }
}
