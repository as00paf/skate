package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.utils.TextureSlots
import kotlinx.serialization.Serializable

@Serializable
enum class ParamType(val components: Int) { FLOAT(1), VEC2(2), VEC3(3), VEC4(4), INT(1), BOOL(1) }

/** A typed material parameter mapped to a shader uniform. */
@Serializable
data class MaterialParamDef(val name: String, val type: ParamType, val uniform: String, val default: List<Float>) {
    init {
        require(default.size == type.components) { "Param '$name' default needs ${type.components} values" }
    }
}

/** A texture slot: sampler uniform, unit, and the optional "has texture" flag uniform. */
@Serializable
data class TextureSlotDef(val name: String, val slot: Int, val samplerUniform: String, val hasUniform: String? = null)

/** Describes what a shader expects from a material. */
@Serializable
data class MaterialSchema(
    val id: String,
    val shader: String,
    val params: List<MaterialParamDef>,
    val textures: List<TextureSlotDef>,
) {
    val floatCount: Int = params.sumOf { it.type.components }
    fun param(name: String): MaterialParamDef? = params.firstOrNull { it.name == name }

    companion object {
        const val PBR_ID = "pbr-metallic-roughness"

        object Param {
            const val BASE_COLOR = "baseColor"
            const val BASE_COLOR_FACTOR = "baseColorFactor"
            const val METALLIC_FACTOR = "metallicFactor"
            const val ROUGHNESS_FACTOR = "roughnessFactor"
            const val EMISSIVE_FACTOR = "emissiveFactor"
            const val ALPHA_MODE = "alphaMode"
            const val ALPHA_CUTOFF = "alphaCutoff"
        }

        object Tex {
            const val BASE_COLOR = "baseColor"
            const val NORMAL = "normal"
            const val METALLIC_ROUGHNESS = "metallicRoughness"
            const val AO = "ao"
            const val EMISSIVE = "emissive"
        }

        /** Built-in default; slot numbers and uniform names match the existing 3D shader. */
        val PBR = MaterialSchema(
            id = PBR_ID,
            shader = "default3d",
            params = listOf(
                MaterialParamDef(Param.BASE_COLOR, ParamType.VEC4, Uniforms.BASE_COLOR, listOf(1f, 1f, 1f, 1f)),
                MaterialParamDef(Param.BASE_COLOR_FACTOR, ParamType.FLOAT, Uniforms.BASE_COLOR_FACTOR, listOf(1f)),
                MaterialParamDef(Param.METALLIC_FACTOR, ParamType.FLOAT, Uniforms.METALLIC_FACTOR, listOf(0f)),
                MaterialParamDef(Param.ROUGHNESS_FACTOR, ParamType.FLOAT, Uniforms.ROUGHNESS_FACTOR, listOf(0.5f)),
                MaterialParamDef(Param.EMISSIVE_FACTOR, ParamType.VEC3, Uniforms.EMISSIVE_FACTOR, listOf(0f, 0f, 0f)),
                MaterialParamDef(Param.ALPHA_MODE, ParamType.INT, Uniforms.ALPHA_MODE, listOf(0f)),
                MaterialParamDef(Param.ALPHA_CUTOFF, ParamType.FLOAT, Uniforms.ALPHA_CUTOFF, listOf(0.5f)),
            ),
            textures = listOf(
                TextureSlotDef(Tex.BASE_COLOR, TextureSlots.BASE_COLOR, Uniforms.BASE_COLOR_TEXTURE, Uniforms.HAS_BASE_COLOR_TEXTURE),
                TextureSlotDef(Tex.NORMAL, TextureSlots.NORMAL, Uniforms.NORMAL_MAP, Uniforms.HAS_NORMAL_MAP),
                TextureSlotDef(Tex.METALLIC_ROUGHNESS, TextureSlots.METALLIC_ROUGHNESS, Uniforms.METALLIC_ROUGHNESS_TEXTURE, Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE),
                TextureSlotDef(Tex.AO, TextureSlots.AO, Uniforms.AO_TEXTURE, Uniforms.HAS_AO_TEXTURE),
                TextureSlotDef(Tex.EMISSIVE, TextureSlots.EMISSIVE, Uniforms.EMISSIVE_TEXTURE, Uniforms.HAS_EMISSIVE_TEXTURE),
            ),
        )
    }
}

/** Known schemas by id. */
object MaterialSchemas {
    private val schemas = HashMap<String, MaterialSchema>().apply { put(MaterialSchema.PBR_ID, MaterialSchema.PBR) }

    fun register(schema: MaterialSchema) { schemas[schema.id] = schema }
    fun get(id: String): MaterialSchema? = schemas[id]
    fun require(id: String): MaterialSchema = get(id) ?: throw IllegalArgumentException("Unknown material schema '$id'")
}
