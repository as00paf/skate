package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

/** Standalone, serializable material (`.skmat`). Only parameters differing from schema defaults are stored. */
@Serializable
data class MaterialAsset(
    val name: String = "Material",
    val schema: String = MaterialSchema.PBR_ID,
    val params: Map<String, List<Float>> = emptyMap(),
    val textures: Map<String, String> = emptyMap(),
    val doubleSided: Boolean = false,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val version: Int = CURRENT_VERSION,
) {
    /** Builds a runtime material; unknown/invalid params are rejected, missing ones use defaults. */
    fun toMaterial(resolveTexture: (String) -> Texture?): Material {
        val s = MaterialSchemas.require(schema)
        for ((key, value) in params) {
            val def = s.param(key) ?: throw IllegalArgumentException("Unknown param '$key' for schema '$schema'")
            require(value.size == def.type.components) { "Param '$key' needs ${def.type.components} values" }
        }
        fun f(name: String): List<Float> = params[name] ?: s.param(name)!!.default
        fun tex(slot: String): Texture? = textures[slot]?.let(resolveTexture)
        val c = f(MaterialSchema.Companion.Param.BASE_COLOR)
        val e = f(MaterialSchema.Companion.Param.EMISSIVE_FACTOR)
        return Material(
            baseColorTexture = tex(MaterialSchema.Companion.Tex.BASE_COLOR),
            normalMap = tex(MaterialSchema.Companion.Tex.NORMAL),
            metallicRoughnessTexture = tex(MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS),
            aoTexture = tex(MaterialSchema.Companion.Tex.AO),
            emissiveTexture = tex(MaterialSchema.Companion.Tex.EMISSIVE),
            baseColor = Vector4f(c[0], c[1], c[2], c[3]),
            baseColorFactor = f(MaterialSchema.Companion.Param.BASE_COLOR_FACTOR)[0],
            metallicFactor = f(MaterialSchema.Companion.Param.METALLIC_FACTOR)[0],
            roughnessFactor = f(MaterialSchema.Companion.Param.ROUGHNESS_FACTOR)[0],
            emissiveFactor = Vector3f(e[0], e[1], e[2]),
            doubleSided = doubleSided,
            alphaMode = alphaMode,
            alphaCutoff = alphaCutoff,
        )
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    fun save(file: File) = file.writeText(toJson())

    companion object {
        const val CURRENT_VERSION = 1
        const val EXTENSION = "skmat"

        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): MaterialAsset {
            val asset = json.decodeFromString(serializer(), text)
            require(asset.version <= CURRENT_VERSION) { "Material version ${asset.version} is newer than supported $CURRENT_VERSION" }
            return asset
        }

        fun load(file: File): MaterialAsset = fromJson(file.readText())

        /** Captures [material]; textures are stored by [Texture.filePath] (textures without a path are omitted). */
        fun from(material: Material, name: String = "Material"): MaterialAsset {
            val s = MaterialSchema.PBR
            val out = HashMap<String, List<Float>>()
            for (p in s.params) {
                if (p.name == MaterialSchema.Companion.Param.ALPHA_MODE || p.name == MaterialSchema.Companion.Param.ALPHA_CUTOFF) continue
                val buf = FloatArray(p.type.components)
                material.readParam(p.name, buf, 0)
                if (buf.toList() != p.default) out[p.name] = buf.toList()
            }
            val tex = HashMap<String, String>()
            for (t in s.textures) material.textureFor(t.name)?.filePath?.let { tex[t.name] = it }
            return MaterialAsset(name, s.id, out, tex, material.doubleSided, material.alphaMode, material.alphaCutoff)
        }
    }
}
