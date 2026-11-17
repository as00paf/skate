package com.pafoid.skate.engine.assets.gltf

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One triangle list for [GlbWriter]: 3 floats per vertex in [positions] and [normals], 2 in [uvs] (may be empty),
 * 3 [indices] per triangle. [baseColor] is the glTF material's factor (RGBA); the `.skmat` that uses the file binds
 * the textures.
 */
class GlbMesh(
    val name: String,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val baseColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
    val isEmpty: Boolean get() = indices.isEmpty()

    init {
        require(positions.size % 3 == 0 && normals.size == positions.size) { "$name: positions/normals size mismatch" }
        require(uvs.isEmpty() || uvs.size / 2 == vertexCount) { "$name: uv count mismatch" }
        require(indices.size % 3 == 0 && indices.all { it in 0 until vertexCount }) { "$name: bad indices" }
    }
}

/**
 * Writes binary glTF 2.0 (`.glb`, AST-17 §4.7): one node and mesh per file, one primitive per [GlbMesh] (positions,
 * normals, optional UVs, 32-bit indices), each with a material named after its mesh. No textures, skins or animations.
 * Empty meshes are skipped. Loaders (Assimp) read it back with the same vertex and index counts.
 */
object GlbWriter {
    private const val MAGIC = 0x46546C67 // "glTF"
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942
    private const val FLOAT = 5126
    private const val UINT = 5125
    private const val ARRAY_BUFFER = 34962
    private const val ELEMENT_ARRAY_BUFFER = 34963

    fun write(file: File, meshName: String, parts: List<GlbMesh>) {
        file.parentFile?.mkdirs()
        file.writeBytes(encode(meshName, parts))
    }

    fun encode(meshName: String, parts: List<GlbMesh>): ByteArray {
        val meshes = parts.filterNot { it.isEmpty }
        require(meshes.isNotEmpty()) { "$meshName: nothing to write" }
        val bin = ByteArrayOutputStream()
        val views = ArrayList<JsonObject>()
        val accessors = ArrayList<JsonObject>()

        fun view(bytes: ByteArray, target: Int): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            views += buildJsonObject {
                put("buffer", 0); put("byteOffset", bin.size()); put("byteLength", bytes.size); put("target", target)
            }
            bin.write(bytes)
            return views.size - 1
        }

        fun floats(values: FloatArray, components: Int, type: String, bounds: Boolean): Int {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putFloat)
            val v = view(buffer.array(), ARRAY_BUFFER)
            accessors += buildJsonObject {
                put("bufferView", v); put("componentType", FLOAT); put("count", values.size / components); put("type", type)
                if (bounds) {
                    val min = FloatArray(components) { Float.POSITIVE_INFINITY }
                    val max = FloatArray(components) { Float.NEGATIVE_INFINITY }
                    values.forEachIndexed { i, f -> val c = i % components; if (f < min[c]) min[c] = f; if (f > max[c]) max[c] = f }
                    put("min", JsonArray(min.map { JsonPrimitive(it) })); put("max", JsonArray(max.map { JsonPrimitive(it) }))
                }
            }
            return accessors.size - 1
        }

        val primitives = meshes.mapIndexed { m, mesh ->
            val position = floats(mesh.positions, 3, "VEC3", bounds = true)
            val normal = floats(mesh.normals, 3, "VEC3", bounds = false)
            val uv = if (mesh.uvs.isNotEmpty()) floats(mesh.uvs, 2, "VEC2", bounds = false) else null
            val ib = ByteBuffer.allocate(mesh.indices.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            mesh.indices.forEach(ib::putInt)
            val iv = view(ib.array(), ELEMENT_ARRAY_BUFFER)
            accessors += buildJsonObject { put("bufferView", iv); put("componentType", UINT); put("count", mesh.indices.size); put("type", "SCALAR") }
            val indices = accessors.size - 1
            buildJsonObject {
                put("attributes", buildJsonObject {
                    put("POSITION", position); put("NORMAL", normal); uv?.let { put("TEXCOORD_0", it) }
                })
                put("indices", indices); put("material", m); put("mode", 4)
            }
        }
        while (bin.size() % 4 != 0) bin.write(0)

        val root = buildJsonObject {
            put("asset", buildJsonObject { put("version", "2.0"); put("generator", "Skate GlbWriter") })
            put("scene", 0)
            put("scenes", buildJsonArray { add(buildJsonObject { put("nodes", buildJsonArray { add(JsonPrimitive(0)) }) }) })
            put("nodes", buildJsonArray { add(buildJsonObject { put("name", meshName); put("mesh", 0) }) })
            put("meshes", buildJsonArray { add(buildJsonObject { put("name", meshName); put("primitives", JsonArray(primitives)) }) })
            put("materials", JsonArray(meshes.map { mesh ->
                buildJsonObject {
                    put("name", mesh.name)
                    put("pbrMetallicRoughness", buildJsonObject {
                        put("baseColorFactor", JsonArray(mesh.baseColor.map { JsonPrimitive(it) }))
                        put("metallicFactor", 0f); put("roughnessFactor", 0.9f)
                    })
                }
            }))
            put("accessors", JsonArray(accessors))
            put("bufferViews", JsonArray(views))
            put("buffers", buildJsonArray { add(buildJsonObject { put("byteLength", bin.size()) }) })
        }
        var json = root.toString().toByteArray(Charsets.UTF_8)
        if (json.size % 4 != 0) json += ByteArray(4 - json.size % 4) { ' '.code.toByte() }
        val body = bin.toByteArray()
        val total = 12 + 8 + json.size + 8 + body.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(MAGIC); putInt(2); putInt(total)
            putInt(json.size); putInt(JSON_CHUNK); put(json)
            putInt(body.size); putInt(BIN_CHUNK); put(body)
        }.array()
    }
}
