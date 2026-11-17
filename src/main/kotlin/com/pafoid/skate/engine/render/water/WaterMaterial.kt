package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialParamDef
import com.pafoid.skate.engine.render.material.MaterialSchema
import com.pafoid.skate.engine.render.material.MaterialSchemas
import com.pafoid.skate.engine.render.material.ParamType
import com.pafoid.skate.engine.render.material.TextureSlotDef
import com.pafoid.skate.engine.render.material.UniformSink
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms

/**
 * The `"water"` material schema (REN-29 §6.5) and its runtime form. A water `.skmat` stores only what differs from the
 * defaults below; textures are optional (without them `water.glsl` uses procedural detail normals and foam).
 */
object WaterSchema {
    const val ID = "water"

    object Param {
        const val SHALLOW_COLOR = "shallowColor"
        const val DEEP_COLOR = "deepColor"
        const val ABSORPTION_DISTANCE = "absorptionDistance"
        const val ROUGHNESS = "roughness"
        const val REFRACTION_STRENGTH = "refractionStrength"
        const val NORMAL_SCALE = "normalScale"
        const val NORMAL_TILING = "normalTiling"
        const val FOAM_DISTANCE = "foamDistance"
        const val FOAM_COLOR = "foamColor"
        const val SSR_ENABLED = "ssrEnabled"
    }

    object Tex {
        const val NORMAL_A = "normalA"
        const val NORMAL_B = "normalB"
        const val FOAM = "foam"
    }

    val SCHEMA = MaterialSchema(
        id = ID,
        shader = "engine:Shaders/water.glsl",
        params = listOf(
            MaterialParamDef(Param.SHALLOW_COLOR, ParamType.VEC3, "uShallowColor", listOf(0.55f, 0.8f, 0.78f)),
            MaterialParamDef(Param.DEEP_COLOR, ParamType.VEC3, "uDeepColor", listOf(0.02f, 0.12f, 0.16f)),
            MaterialParamDef(Param.ABSORPTION_DISTANCE, ParamType.FLOAT, "uAbsorptionDistance", listOf(3f)),
            MaterialParamDef(Param.ROUGHNESS, ParamType.FLOAT, "uWaterRoughness", listOf(0.04f)),
            MaterialParamDef(Param.REFRACTION_STRENGTH, ParamType.FLOAT, "uRefractionStrength", listOf(0.03f)),
            MaterialParamDef(Param.NORMAL_SCALE, ParamType.VEC2, "uNormalScale", listOf(0.35f, 0.25f)),
            MaterialParamDef(Param.NORMAL_TILING, ParamType.VEC2, "uNormalTiling", listOf(0.15f, 0.37f)),
            MaterialParamDef(Param.FOAM_DISTANCE, ParamType.FLOAT, "uFoamDistance", listOf(0.6f)),
            MaterialParamDef(Param.FOAM_COLOR, ParamType.VEC3, "uFoamColor", listOf(0.95f, 0.97f, 1f)),
            MaterialParamDef(Param.SSR_ENABLED, ParamType.BOOL, "uSsrEnabled", listOf(1f)),
        ),
        textures = listOf(
            TextureSlotDef(Tex.NORMAL_A, Uniforms.WATER_NORMAL_A_UNIT, Uniforms.WATER_NORMAL_A, "uHasNormalA"),
            TextureSlotDef(Tex.NORMAL_B, Uniforms.WATER_NORMAL_B_UNIT, Uniforms.WATER_NORMAL_B, "uHasNormalB"),
            TextureSlotDef(Tex.FOAM, Uniforms.WATER_FOAM_UNIT, Uniforms.WATER_FOAM, "uHasFoam"),
        ),
    )

    /** Registers the schema so `.skmat` files with `"schema": "water"` resolve (idempotent). */
    fun register() = MaterialSchemas.register(SCHEMA)

    /** The bundled presets as material assets; a project `.skmat` at the same path replaces one. */
    val BUILT_IN: Map<String, MaterialAsset> = mapOf(
        WaterMaterials.DEFAULT to MaterialAsset(name = "Water (lake)", schema = ID),
        WaterMaterials.POOL to MaterialAsset(
            name = "Water (pool)", schema = ID,
            params = mapOf(
                Param.SHALLOW_COLOR to listOf(0.6f, 0.92f, 0.95f),
                Param.DEEP_COLOR to listOf(0.05f, 0.35f, 0.55f),
                Param.ABSORPTION_DISTANCE to listOf(6f),
                Param.ROUGHNESS to listOf(0.02f),
                Param.FOAM_DISTANCE to listOf(0.25f),
            ),
        ),
        WaterMaterials.OCEAN to MaterialAsset(
            name = "Water (ocean)", schema = ID,
            params = mapOf(
                Param.SHALLOW_COLOR to listOf(0.3f, 0.6f, 0.62f),
                Param.DEEP_COLOR to listOf(0.01f, 0.06f, 0.12f),
                Param.ABSORPTION_DISTANCE to listOf(8f),
                Param.ROUGHNESS to listOf(0.06f),
                Param.NORMAL_SCALE to listOf(0.5f, 0.35f),
                Param.NORMAL_TILING to listOf(0.06f, 0.16f),
                Param.FOAM_DISTANCE to listOf(1.2f),
            ),
        ),
    )
}

/** A resolved water material: every schema parameter (asset values over defaults) and its optional textures. */
class WaterMaterial(val name: String, val values: FloatArray, val textures: Map<String, Texture>) {

    /** Value(s) of [param], read from [values] in schema order. */
    fun param(param: String): FloatArray {
        var offset = 0
        for (p in WaterSchema.SCHEMA.params) {
            if (p.name == param) return values.copyOfRange(offset, offset + p.type.components)
            offset += p.type.components
        }
        throw IllegalArgumentException("Unknown water param '$param'")
    }

    /** Uploads the parameters and sampler units; the caller binds [textures] to their slots. */
    fun upload(sink: UniformSink) {
        var o = 0
        for (p in WaterSchema.SCHEMA.params) {
            when (p.type) {
                ParamType.FLOAT -> sink.setFloat(p.uniform, values[o])
                ParamType.INT -> sink.setInt(p.uniform, values[o].toInt())
                ParamType.BOOL -> sink.setBool(p.uniform, values[o] != 0f)
                ParamType.VEC2 -> sink.setVec2(p.uniform, values[o], values[o + 1])
                ParamType.VEC3 -> sink.setVec3(p.uniform, values[o], values[o + 1], values[o + 2])
                ParamType.VEC4 -> sink.setVec4(p.uniform, values[o], values[o + 1], values[o + 2], values[o + 3])
            }
            o += p.type.components
        }
        for (t in WaterSchema.SCHEMA.textures) {
            sink.setInt(t.samplerUniform, t.slot)
            t.hasUniform?.let { sink.setBool(it, textures.containsKey(t.name)) }
        }
    }

    companion object {
        /** Builds the runtime material; rejects other schemas and unknown or mis-sized params. */
        fun from(asset: MaterialAsset, resolveTexture: (String) -> Texture?): WaterMaterial {
            require(asset.schema == WaterSchema.ID) { "Material '${asset.name}' has schema '${asset.schema}', not '${WaterSchema.ID}'" }
            val schema = WaterSchema.SCHEMA
            for ((key, value) in asset.params) {
                val def = schema.param(key) ?: throw IllegalArgumentException("Unknown param '$key' for schema '${schema.id}'")
                require(value.size == def.type.components) { "Param '$key' needs ${def.type.components} values" }
            }
            val values = FloatArray(schema.floatCount)
            var o = 0
            for (p in schema.params) {
                val v = asset.params[p.name] ?: p.default
                for (c in v.indices) values[o + c] = v[c]
                o += p.type.components
            }
            val textures = HashMap<String, Texture>()
            for (t in schema.textures) asset.textures[t.name]?.let(resolveTexture)?.let { textures[t.name] = it }
            return WaterMaterial(asset.name, values, textures)
        }

        /** Loads [reference]: a project `.skmat` read through [readText], else a bundled preset, else the default. */
        fun resolve(reference: String, readText: (String) -> String?, resolveTexture: (String) -> Texture?): WaterMaterial {
            val asset = runCatching { readText(reference)?.takeIf { it.isNotBlank() }?.let(MaterialAsset::fromJson) }.getOrNull()
                ?.takeIf { it.schema == WaterSchema.ID }
                ?: WaterSchema.BUILT_IN[reference]
                ?: WaterSchema.BUILT_IN.getValue(WaterMaterials.DEFAULT)
            return from(asset, resolveTexture)
        }
    }
}
